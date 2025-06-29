(ns pebbles.sqs.consumer-integration-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [cheshire.core :as json]
   [cognitect.aws.client.api]
   [com.stuartsierra.component :as component]
   [pebbles.sqs.consumer :as sqs-consumer]
   [pebbles.sqs.sqs-test-utils :as sqs-utils]
   [pebbles.test-utils :as test-utils]
   [pebbles.db :as db]
   [pebbles.handlers :as handlers]
   [pebbles.system :as system]))

;; Global state for shared localstack instance
(def ^:dynamic *sqs-env* nil)
(def ^:dynamic *test-db* nil)
(def test-client-krn "krn:clnt:test-client")

(defn sqs-integration-fixture
  "Start localstack once for all integration tests in this namespace"
  [f]
  (println "Starting localstack for integration tests...")
  (let [sqs-env (sqs-utils/fresh-sqs)]
    (binding [*sqs-env* sqs-env]
      (try
        (f)
        (finally
          (println "Cleaning up localstack...")
          (sqs-utils/cleanup-sqs sqs-env))))))

(defn db-fixture [f]
  (let [db-map (test-utils/fresh-db)]
    (binding [*test-db* (:db db-map)]
      (try
        (f)
        (finally
          (test-utils/cleanup-db db-map))))))

(use-fixtures :once sqs-integration-fixture)
(use-fixtures :each db-fixture)

;; ----------------------------------------------------------------------------
;; SQS Integration Tests
;; ----------------------------------------------------------------------------

(deftest ^:integration sqs-integration-test
  (testing "Send and process SQS message end-to-end"
    (let [queue-url (sqs-utils/create-test-queue (:sqs-client *sqs-env*) "test-progress-queue")
          test-message {:filename "integration-test.csv"
                       :counts {:done 50 :warn 3 :failed 2}
                       :total 500
                       :clientKrn test-client-krn
                       :email "integration@example.com"}]
      
      (try
        ;; Send message to SQS
        (sqs-utils/send-test-message (:sqs-client *sqs-env*) queue-url test-message)
        
        ;; Receive and process message
        (let [receive-result (cognitect.aws.client.api/invoke (:sqs-client *sqs-env*) 
                                                            {:op :ReceiveMessage
                                                             :request {:QueueUrl queue-url
                                                                      :MaxNumberOfMessages 1}})
              messages (:Messages receive-result)]
          
          (is (= 1 (count messages)))
          
          (let [message (first messages)
                result (sqs-consumer/process-progress-message *test-db* message)]
            
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
              (is (= 500 (:total saved))))))
        
        (finally
          ;; Clean up the test queue
          (cognitect.aws.client.api/invoke (:sqs-client *sqs-env*) 
                                         {:op :DeleteQueue
                                          :request {:QueueUrl queue-url}}))))))

(deftest ^:integration sqs-consumer-component-test
  (testing "SQS consumer component lifecycle"
    (let [queue-url (sqs-utils/create-test-queue (:sqs-client *sqs-env*) "component-test-queue")
          mongo-component (component/start (system/map->MongoComponent 
                                            {:uri (or (System/getenv "MONGO_URI") "mongodb://localhost:27017/test")}))
          consumer (sqs-consumer/make-sqs-consumer
                    :queue-url queue-url
                    :region "us-east-1"
                    :endpoint-override (:endpoint *sqs-env*)
                    :polling-enabled false ; Disable polling for test
                    :credentials {:aws/access-key-id "test"
                                 :aws/secret-access-key "test"})
          system (component/system-map
                  :mongo mongo-component
                  :sqs-consumer (component/using consumer [:mongo]))]
      
      (try
        ;; Start the system
        (let [started-system (component/start system)]
          (is (not (nil? (get-in started-system [:sqs-consumer :sqs-client]))))
          
          ;; Send a test message
          (sqs-utils/send-test-message (:sqs-client *sqs-env*) queue-url
                                     {:filename "component-test.csv"
                                      :counts {:done 30 :warn 1 :failed 0}
                                      :clientKrn test-client-krn
                                      :email "component@example.com"})
          
          ;; Manually process one message (since polling is disabled)
          (let [receive-result (cognitect.aws.client.api/invoke (:sqs-client *sqs-env*) 
                                                              {:op :ReceiveMessage
                                                               :request {:QueueUrl queue-url
                                                                        :MaxNumberOfMessages 1}})
                messages (:Messages receive-result)]
            (when (seq messages)
              (let [message (first messages)
                    result (sqs-consumer/process-progress-message (:db mongo-component) message)]
                (is (= :success (:status result)))
                
                ;; Verify in database
                (let [saved (db/find-progress (:db mongo-component) test-client-krn "component-test.csv" "component@example.com")]
                  (is (= "component-test.csv" (:filename saved)))
                  (is (= {:done 30 :warn 1 :failed 0} (:counts saved)))))))
          
          ;; Stop the system
          (component/stop started-system))
        
        (finally 
          (component/stop mongo-component)
          ;; Clean up the test queue
          (cognitect.aws.client.api/invoke (:sqs-client *sqs-env*) 
                                         {:op :DeleteQueue
                                          :request {:QueueUrl queue-url}}))))))

(deftest ^:integration http-sqs-payload-compatibility-test
  (testing "Same payload works for both HTTP and SQS"
    (let [queue-url (sqs-utils/create-test-queue (:sqs-client *sqs-env*) "compatibility-test-queue")
          payload {:filename "compatibility-test.csv"
                   :counts {:done 25 :warn 1 :failed 0}
                   :total 250
                   :clientKrn test-client-krn
                   :email "compat@example.com"}]
      
      (try
        ;; Process via SQS
        (let [sqs-message {:Body (json/generate-string payload)}
              sqs-result (sqs-consumer/process-progress-message *test-db* sqs-message)
              
              ;; Process via HTTP handlers directly (simulating HTTP path)
              http-result (handlers/create-new-progress 
                          *test-db* 
                          (:clientKrn payload)
                          (:filename payload)
                          (:email payload)
                          (:counts payload)
                          (:total payload)
                          (:isLast payload)
                          (:errors payload)
                          (:warnings payload)
                          (.toString (java.time.Instant/now)))
              http-parsed (json/parse-string (:body http-result) true)]
          
          ;; Both should succeed
          (is (= :success (:status sqs-result)))
          (is (= 200 (:status http-result)))  ; HTTP success status
          
          ;; Results should be equivalent
          (is (= "created" (get-in sqs-result [:result :result])))
          (is (= "created" (:result http-parsed)))
          (is (= (:filename payload) (get-in sqs-result [:result :filename])))
          (is (= (:filename payload) (:filename http-parsed)))
          (is (= (:counts payload) (get-in sqs-result [:result :counts])))
          (is (= (:counts payload) (:counts http-parsed))))
        
        (finally
          ;; Clean up the test queue
          (cognitect.aws.client.api/invoke (:sqs-client *sqs-env*) 
                                         {:op :DeleteQueue
                                          :request {:QueueUrl queue-url}})))))) 