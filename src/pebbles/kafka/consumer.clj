(ns pebbles.kafka.consumer
  (:require
   [clojure.tools.logging :as log]
   [cheshire.core :as json]
   [com.stuartsierra.component :as component]
   [clojure.spec.alpha :as s]
   [pebbles.handlers :as handlers]
   [pebbles.specs :as specs]
   [pebbles.db :as db])
  (:import
   (org.apache.kafka.clients.consumer KafkaConsumer ConsumerRecord)
   (org.apache.kafka.common.serialization StringDeserializer)
   (java.time Duration)
   (java.util Properties)))

;; ----------------------------------------------------------------------------
;; Kafka Client Configuration
;; ----------------------------------------------------------------------------

(defn create-kafka-consumer
  "Create Kafka consumer with configuration"
  [& {:keys [bootstrap-servers group-id topic auto-offset-reset consumer-props]
      :or {bootstrap-servers "localhost:9092"
           group-id "pebbles-progress-consumer"
           auto-offset-reset "earliest"}}]
  (let [props (doto (Properties.)
                (.put "bootstrap.servers" bootstrap-servers)
                (.put "group.id" group-id)
                (.put "key.deserializer" (.getName StringDeserializer))
                (.put "value.deserializer" (.getName StringDeserializer))
                (.put "auto.offset.reset" auto-offset-reset)
                (.put "enable.auto.commit" "false"))] ; Manual commit for better error handling
    ;; Add any additional consumer properties
    (when consumer-props
      (doseq [[k v] consumer-props]
        (.put props (name k) (str v))))
    (let [consumer (KafkaConsumer. props)]
      (.subscribe consumer [topic])
      consumer)))

;; ----------------------------------------------------------------------------
;; Message Processing
;; ----------------------------------------------------------------------------

(defn parse-kafka-message
  "Parse Kafka message value and extract progress update data"
  [^ConsumerRecord record]
  (try
    (let [value (.value record)
          parsed (json/parse-string value true)]
      ;; Validate using spec
      (if (s/valid? ::specs/kafka-progress-message parsed)
        parsed
        (let [explain-data (s/explain-data ::specs/kafka-progress-message parsed)
              error-msg (str "Invalid Kafka message format: " (s/explain-str ::specs/kafka-progress-message parsed))]
          (throw (ex-info error-msg {:parsed parsed :explain-data explain-data})))))
    (catch Exception e
      (log/error "Failed to parse Kafka message" {:record record :error (.getMessage e)})
      (throw e))))

(defn process-progress-message
  "Process a single progress update message using the same logic as HTTP handler"
  [db ^ConsumerRecord record]
  (try
    (let [{:keys [clientKrn email filename counts total isLast errors warnings]} (parse-kafka-message record)
          now (.toString (java.time.Instant/now))
          any-existing (db/find-progress-by-filename db clientKrn filename)
          existing (db/find-progress db clientKrn filename email)
          validation-error (handlers/validate-progress-update-request email clientKrn any-existing existing)]
      
      (if validation-error
        (do
          (log/warn "Validation failed for Kafka message" 
                   {:clientKrn clientKrn :filename filename :email email :error (:body validation-error)
                    :topic (.topic record) :partition (.partition record) :offset (.offset record)})
          {:status :validation-error :error (:body validation-error)})
        (let [response (if (nil? existing)
                        (handlers/create-new-progress db clientKrn filename email counts total isLast errors warnings now)
                        (handlers/update-existing-progress db clientKrn filename email existing counts total isLast errors warnings now))
              result (json/parse-string (:body response) true)]
          (log/info "Successfully processed Kafka progress update" 
                   {:clientKrn clientKrn :filename filename :email email :result (:result result)
                    :topic (.topic record) :partition (.partition record) :offset (.offset record)})
          {:status :success :result result})))
    (catch Exception e
      (log/error "Error processing Kafka progress message" {:record record :error (.getMessage e)})
      {:status :error :error (.getMessage e)})))

;; ----------------------------------------------------------------------------
;; Kafka Consumer Component
;; ----------------------------------------------------------------------------

(defrecord KafkaConsumerComponent [bootstrap-servers group-id topic auto-offset-reset consumer-props polling-enabled mongo kafka-consumer consumer-thread]
  component/Lifecycle
  
  (start [this]
    (if consumer-thread
      this
      (let [consumer (create-kafka-consumer :bootstrap-servers bootstrap-servers
                                           :group-id group-id
                                           :topic topic
                                           :auto-offset-reset auto-offset-reset
                                           :consumer-props consumer-props)
            db (:db mongo)
            running (atom true)
            thread (Thread.
                    (fn []
                      (log/info "Starting Kafka consumer" {:bootstrap-servers bootstrap-servers :group-id group-id :topic topic})
                      (while @running
                        (try
                          (when polling-enabled
                            (let [records (.poll consumer (Duration/ofMillis 1000))]
                              (when (not (.isEmpty records))
                                (log/debug "Received Kafka messages" {:count (.count records)})
                                (doseq [^ConsumerRecord record records]
                                  (let [result (process-progress-message db record)]
                                    (when (= :success (:status result))
                                      ;; Commit offset only on successful processing
                                      (.commitSync consumer)))))))
                          (catch InterruptedException _
                            (log/info "Kafka consumer interrupted")
                            (reset! running false))
                          (catch Exception e
                            (log/error "Error in Kafka consumer loop" {:error (.getMessage e)})
                            (Thread/sleep 5000)))))
                    "kafka-consumer")]
        (.start thread)
        (assoc this :kafka-consumer consumer :consumer-thread thread))))
  
  (stop [this]
    (when consumer-thread
      (log/info "Stopping Kafka consumer")
      (.interrupt consumer-thread)
      (try
        (.join consumer-thread 5000)
        (catch InterruptedException _
          (log/warn "Timeout waiting for Kafka consumer thread to stop"))))
    (when kafka-consumer
      (try
        (.close kafka-consumer)
        (catch Exception e
          (log/warn "Error closing Kafka consumer" {:error (.getMessage e)}))))
    (assoc this :kafka-consumer nil :consumer-thread nil)))

;; ----------------------------------------------------------------------------
;; Component Factory
;; ----------------------------------------------------------------------------

(defn make-kafka-consumer
  "Create Kafka consumer component with configuration"
  [& {:keys [bootstrap-servers group-id topic auto-offset-reset consumer-props polling-enabled]
      :or {bootstrap-servers "localhost:9092"
           group-id "pebbles-progress-consumer"
           topic "progress-updates"
           auto-offset-reset "earliest"
           polling-enabled true}}]
  (map->KafkaConsumerComponent {:bootstrap-servers bootstrap-servers
                               :group-id group-id
                               :topic topic
                               :auto-offset-reset auto-offset-reset
                               :consumer-props consumer-props
                               :polling-enabled polling-enabled})) 