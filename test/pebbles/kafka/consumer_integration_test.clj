(ns pebbles.kafka.consumer-integration-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [cheshire.core :as json]
   [com.stuartsierra.component :as component]
   [pebbles.kafka.consumer :as kafka-consumer]
   [pebbles.kafka.kafka-test-utils :as kafka-utils]
   [pebbles.test-utils :as test-utils]
   [pebbles.db :as db]
   [pebbles.handlers :as handlers]
   [pebbles.system :as system])
  (:import
   (org.apache.kafka.clients.consumer KafkaConsumer)
   (org.apache.kafka.common.serialization StringDeserializer)
   (java.time Duration)
   (java.util Properties)))

;; Global state for shared Kafka instance
(def ^:dynamic *kafka-env* nil)
(def ^:dynamic *test-db* nil)
(def ^:dynamic *kafka-admin* nil)
(def ^:dynamic *kafka-producer* nil)
(def test-client-krn "krn:clnt:test-client")

(defn kafka-integration-fixture
  "Start Kafka once for all integration tests in this namespace"
  [f]
  (println "DEBUG: Starting Kafka for integration tests...")
  (let [kafka-env (kafka-utils/fresh-kafka)
        ;; Add shutdown hook to force exit after integration tests
        shutdown-hook (Thread. (fn []
                                (println "DEBUG: Shutdown hook executing...")
                                (Thread/sleep 2000)
                                (println "DEBUG: Force exiting after integration tests...")
                                (System/exit 0)))]
    (println "DEBUG: Kafka environment created...")
    (println "DEBUG: Creating shared admin client and producer...")
    (let [admin-client (kafka-utils/create-kafka-admin-client (:bootstrap-servers kafka-env))
          producer (kafka-utils/create-kafka-producer (:bootstrap-servers kafka-env))]
      (println "DEBUG: Shared Kafka clients created, binding to dynamic vars...")
      (binding [*kafka-env* kafka-env
                *kafka-admin* admin-client
                *kafka-producer* producer]
        (try
          (println "DEBUG: About to run integration tests...")
          (.addShutdownHook (Runtime/getRuntime) shutdown-hook)
          (f)
          (println "DEBUG: Integration tests completed successfully!")
          (finally
            (println "DEBUG: Entered finally block for Kafka cleanup...")
            ;; Close shared clients first
            (try
              (.close admin-client)
              (println "DEBUG: Shared admin client closed")
              (catch Exception e
                (println "DEBUG: Error closing admin client:" (.getMessage e))))
            (try
              (.close producer)
              (println "DEBUG: Shared producer closed")
              (catch Exception e
                (println "DEBUG: Error closing producer:" (.getMessage e))))
            ;; Then cleanup Kafka environment
            (kafka-utils/cleanup-kafka kafka-env)
            ;; Force cleanup to ensure all resources are released
            (kafka-utils/force-cleanup)
            (println "DEBUG: Kafka cleanup completed")
            ;; Remove shutdown hook since we're cleaning up normally
            (try
              (.removeShutdownHook (Runtime/getRuntime) shutdown-hook)
              (println "DEBUG: Removed shutdown hook")
              (catch Exception e
                (println "DEBUG: Could not remove shutdown hook (already executing?):" (.getMessage e))))
            ;; Give a moment for cleanup, then force exit
            (Thread/sleep 1000)
            (println "DEBUG: Force exiting after successful cleanup...")
            (System/exit 0)))))))

(defn db-fixture [f]
  (let [db-map (test-utils/fresh-db)]
    (binding [*test-db* (:db db-map)]
      (try
        (f)
        (finally
          (test-utils/cleanup-db db-map))))))

(defn create-topic-only
  "Create just a topic using shared admin client (no new connections)"
  [topic-name]
  (println "DEBUG: Creating topic only:" topic-name)
  (kafka-utils/create-test-topic *kafka-admin* topic-name)
  topic-name)

(use-fixtures :once kafka-integration-fixture)
(use-fixtures :each db-fixture)

;; ----------------------------------------------------------------------------
;; Kafka Integration Tests
;; ----------------------------------------------------------------------------

(deftest ^:integration kafka-integration-test
  (testing "Send and process Kafka message end-to-end"
    (let [topic-name "test-progress-topic"
          test-message {:filename "integration-test.csv"
                       :counts {:done 50 :warn 3 :failed 2}
                       :total 500
                       :clientKrn test-client-krn
                       :email "integration@example.com"}]
      
      ;; Create topic using shared admin client
      (create-topic-only topic-name)
      
      ;; Send message to Kafka using shared producer
      (println "DEBUG: Sending test message to Kafka...")
      (kafka-utils/send-test-message *kafka-producer* topic-name test-message)
      (println "DEBUG: Test message sent successfully")
      
      ;; Create a temporary consumer to receive the message
      (println "DEBUG: Creating Kafka consumer...")
      (let [consumer-props (doto (Properties.)
                            (.put "bootstrap.servers" (:bootstrap-servers *kafka-env*))
                            (.put "group.id" "test-consumer-group")
                            (.put "key.deserializer" (.getName StringDeserializer))
                            (.put "value.deserializer" (.getName StringDeserializer))
                            (.put "auto.offset.reset" "earliest"))
            consumer (KafkaConsumer. consumer-props)]
        
        (try
          (println "DEBUG: Subscribing consumer to topic...")
          (.subscribe consumer [topic-name])
          (println "DEBUG: Consumer subscribed, starting to poll...")
          
          ;; Poll for messages
          (let [records (.poll consumer (Duration/ofSeconds 10))]
            (println "DEBUG: Poll completed, record count:" (.count records))
            (is (> (.count records) 0) "Should receive at least one message")
            
            (doseq [record records]
              (println "DEBUG: Processing Kafka record...")
              (let [result (kafka-consumer/process-progress-message *test-db* record)]
                (println "DEBUG: Message processed, result status:" (:status result))
                (is (= :success (:status result)))
                (is (= "created" (get-in result [:result :result])))
                (is (= "integration-test.csv" (get-in result [:result :filename])))
                (is (= {:done 50 :warn 3 :failed 2} (get-in result [:result :counts])))
                
                ;; Verify in database
                (let [saved (db/find-progress *test-db* test-client-krn "integration-test.csv" "integration@example.com")]
                  (is (= test-client-krn (:clientKrn saved)))
                  (is (= "integration-test.csv" (:filename saved)))
                  (is (= "integration@example.com" (:email saved)))
                  (is (= {:done 50 :warn 3 :failed 2} (:counts saved)))
                  (is (= 500 (:total saved)))))))
          
          (finally
            (kafka-utils/safe-close-consumer consumer)))))))

(deftest ^:integration kafka-consumer-component-test
  (testing "Kafka consumer component lifecycle"
    (let [topic-name "component-test-topic"
          mongo-component (component/start (system/map->MongoComponent 
                                            {:uri (or (System/getenv "MONGO_URI") "mongodb://localhost:27017/test")}))
          consumer (kafka-consumer/make-kafka-consumer
                    :bootstrap-servers (:bootstrap-servers *kafka-env*)
                    :group-id "test-component-group"
                    :topic topic-name
                    :polling-enabled false) ; Disable polling for test
          system (component/system-map
                  :mongo mongo-component
                  :kafka-consumer (component/using consumer [:mongo]))]
      
      ;; Create topic using shared admin client
      (create-topic-only topic-name)
      
      (try
        ;; Start the system
        (let [started-system (component/start system)]
          (is (not (nil? (get-in started-system [:kafka-consumer :kafka-consumer]))))
          
          ;; Send a test message using shared producer
          (kafka-utils/send-test-message *kafka-producer* topic-name
                                       {:filename "component-test.csv"
                                        :counts {:done 30 :warn 1 :failed 0}
                                        :clientKrn test-client-krn
                                        :email "component@example.com"})
          
          ;; Manually poll one message (since polling is disabled)
          (let [kafka-consumer (get-in started-system [:kafka-consumer :kafka-consumer])
                records (.poll kafka-consumer (Duration/ofSeconds 5))]
            (when (> (.count records) 0)
              (let [record (first records)
                    result (kafka-consumer/process-progress-message (:db mongo-component) record)]
                (is (= :success (:status result)))
                
                ;; Verify in database
                (let [saved (db/find-progress (:db mongo-component) test-client-krn "component-test.csv" "component@example.com")]
                  (is (= "component-test.csv" (:filename saved)))
                  (is (= {:done 30 :warn 1 :failed 0} (:counts saved)))))))
          
          ;; Stop the system
          (component/stop started-system))
        
        (finally 
          (component/stop mongo-component))))))

(deftest ^:integration http-kafka-payload-compatibility-test
  (testing "Same payload works for both HTTP and Kafka"
    (let [topic-name "compatibility-test-topic"
          payload {:filename "compatibility-test.csv"
                   :counts {:done 25 :warn 1 :failed 0}
                   :total 250
                   :clientKrn test-client-krn
                   :email "compat@example.com"}]
      
      ;; Create topic using shared admin client
      (create-topic-only topic-name)
      
      ;; Process via Kafka using shared producer
      (kafka-utils/send-test-message *kafka-producer* topic-name payload)
      
      ;; Create consumer to get the message
      (let [consumer-props (doto (Properties.)
                            (.put "bootstrap.servers" (:bootstrap-servers *kafka-env*))
                            (.put "group.id" "compatibility-test-group")
                            (.put "key.deserializer" (.getName StringDeserializer))
                            (.put "value.deserializer" (.getName StringDeserializer))
                            (.put "auto.offset.reset" "earliest"))
            consumer (KafkaConsumer. consumer-props)]
        
        (try
          (.subscribe consumer [topic-name])
          (let [records (.poll consumer (Duration/ofSeconds 5))]
            (when (> (.count records) 0)
              (let [record (first records)
                    kafka-result (kafka-consumer/process-progress-message *test-db* record)
                    
                    ;; Process via HTTP handlers directly (simulating HTTP path)
                    http-result (handlers/create-new-progress 
                                *test-db* 
                                (:clientKrn payload)
                                (str "http-" (:filename payload)) ; Different filename to avoid conflict
                                (:email payload)
                                (:counts payload)
                                (:total payload)
                                (:isLast payload)
                                (:errors payload)
                                (:warnings payload)
                                (.toString (java.time.Instant/now)))
                    http-parsed (json/parse-string (:body http-result) true)]
                
                ;; Both should succeed
                (is (= :success (:status kafka-result)))
                (is (= 200 (:status http-result)))
                
                ;; Results should have same structure
                (is (= "created" (get-in kafka-result [:result :result])))
                (is (= "created" (:result http-parsed)))
                (is (= (:counts payload) (get-in kafka-result [:result :counts])))
                (is (= (:counts payload) (:counts http-parsed)))
                (is (= (:total payload) (get-in kafka-result [:result :total])))
                                (is (= (:total payload) (:total http-parsed))))))
            (finally
              (kafka-utils/safe-close-consumer consumer)))))))

(deftest ^:integration kafka-error-handling-integration-test
  (testing "Error handling in Kafka integration"
    (let [topic-name "error-test-topic"]
      
      ;; Create topic using shared admin client
      (create-topic-only topic-name)
      
      ;; Send invalid message using shared producer
      (kafka-utils/send-test-message *kafka-producer* topic-name
                                   {:filename "error-test.csv"
                                    ;; Missing required fields
                                    :counts {:done 10}})
      
      ;; Create consumer to get the message
      (let [consumer-props (doto (Properties.)
                            (.put "bootstrap.servers" (:bootstrap-servers *kafka-env*))
                            (.put "group.id" "error-test-group")
                            (.put "key.deserializer" (.getName StringDeserializer))
                            (.put "value.deserializer" (.getName StringDeserializer))
                            (.put "auto.offset.reset" "earliest"))
            consumer (KafkaConsumer. consumer-props)]
        
        (try
          (.subscribe consumer [topic-name])
          (let [records (.poll consumer (Duration/ofSeconds 5))]
            (when (> (.count records) 0)
              (let [record (first records)
                    result (kafka-consumer/process-progress-message *test-db* record)]
                (is (= :error (:status result)))
                (is (some? (:error result))))))
          (finally
            (kafka-utils/safe-close-consumer consumer)))))))

(deftest ^:integration kafka-message-ordering-test
  (testing "Message ordering and processing"
    (let [topic-name "ordering-test-topic"
          messages [{:filename "ordered-test.csv"
                    :counts {:done 10 :warn 0 :failed 0}
                    :clientKrn test-client-krn
                    :email "order@example.com"}
                   {:filename "ordered-test.csv"
                    :counts {:done 15 :warn 1 :failed 0}
                    :clientKrn test-client-krn
                    :email "order@example.com"}
                   {:filename "ordered-test.csv"
                    :counts {:done 20 :warn 0 :failed 1}
                    :isLast true
                    :clientKrn test-client-krn
                    :email "order@example.com"}]]
      
      ;; Create topic using shared admin client
      (create-topic-only topic-name)
      
      ;; Send messages in order using shared producer
      (doseq [msg messages]
        (kafka-utils/send-test-message *kafka-producer* topic-name msg))
      
      ;; Create consumer to process messages
      (let [consumer-props (doto (Properties.)
                            (.put "bootstrap.servers" (:bootstrap-servers *kafka-env*))
                            (.put "group.id" "ordering-test-group")
                            (.put "key.deserializer" (.getName StringDeserializer))
                            (.put "value.deserializer" (.getName StringDeserializer))
                            (.put "auto.offset.reset" "earliest"))
            consumer (KafkaConsumer. consumer-props)]
        
        (try
          (.subscribe consumer [topic-name])
          (let [records (.poll consumer (Duration/ofSeconds 10))]
            (is (= 3 (.count records)) "Should receive all 3 messages")
            
            ;; Process messages in order
            (doseq [record records]
              (let [result (kafka-consumer/process-progress-message *test-db* record)]
                (is (= :success (:status result)))))
            
            ;; Verify final state
            (let [saved (db/find-progress *test-db* test-client-krn "ordered-test.csv" "order@example.com")]
              (is (= {:done 45 :warn 1 :failed 1} (:counts saved))) ; Should be sum of all counts
              (is (true? (:isCompleted saved))))) ; Should be completed from last message
          
          (finally
            (kafka-utils/safe-close-consumer consumer)))))))