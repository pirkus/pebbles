(ns pebbles.kafka.kafka-test-utils
  (:require
   [cheshire.core :as json]
   [clojure.tools.logging :as log])
  (:import
   (org.testcontainers.containers KafkaContainer)
   (org.testcontainers.utility DockerImageName)
   (org.apache.kafka.clients.producer KafkaProducer ProducerRecord)
   (org.apache.kafka.clients.admin AdminClient NewTopic)
   (org.apache.kafka.common.serialization StringSerializer)
   (java.util Properties Collections)
   (java.net Socket)))

;; Environment variable to use existing Kafka instead of Testcontainers
(def use-existing-kafka (System/getenv "USE_EXISTING_KAFKA"))

;; Default Kafka port
(def kafka-port 9092)

(defn kafka-running?
  "Check if Kafka is running locally on default port"
  [host port]
  (try
    (with-open [socket (Socket. host port)]
      (.isConnected socket))
    (catch Exception _ false)))

(defn start-kafka-container []
  (let [container (-> (KafkaContainer. (DockerImageName/parse "confluentinc/cp-kafka:7.5.0"))
                      (.withReuse true))]
    (log/info "Starting Kafka container (this may take 30-60 seconds)...")
    (try
      (.start container)
      (log/info "Kafka container started successfully")
      ;; Give it a moment to fully initialize
      (Thread/sleep 5000)
      container
      (catch Exception e
        (log/error "Failed to start Kafka container:" (.getMessage e))
        (throw e)))))

(defn get-kafka-bootstrap-servers [container]
  (if container
    (.getBootstrapServers container)
    "localhost:9092"))

(defn create-kafka-producer
  "Create Kafka producer for testing"
  [bootstrap-servers]
  (let [props (doto (Properties.)
                (.put "bootstrap.servers" bootstrap-servers)
                (.put "key.serializer" (.getName StringSerializer))
                (.put "value.serializer" (.getName StringSerializer))
                (.put "acks" "all")
                (.put "retries" (Integer. 3))
                (.put "batch.size" (Integer. 16384))
                (.put "linger.ms" (Integer. 1))
                (.put "buffer.memory" (Long. 33554432)))]
    (KafkaProducer. props)))

(defn create-kafka-admin-client
  "Create Kafka admin client for testing"
  [bootstrap-servers]
  (let [props (doto (Properties.)
                (.put "bootstrap.servers" bootstrap-servers))]
    (AdminClient/create props)))

(defn create-test-topic
  "Create a test topic"
  [admin-client topic-name]
  (try
    (let [topic (NewTopic. topic-name 1 (short 1))]
      (.createTopics admin-client (Collections/singletonList topic))
      (log/info "Created test topic:" topic-name)
      topic-name)
    (catch Exception e
      (log/warn "Topic may already exist:" (.getMessage e))
      topic-name)))

(defn send-test-message
  "Send a test message to Kafka topic"
  [producer topic message-data]
  (try
    (let [message-json (json/generate-string message-data)
          record (ProducerRecord. topic nil message-json)
          future (.send producer record)]
      (.get future) ; Wait for send to complete
      (log/debug "Sent test message to Kafka:" message-data)
      message-data)
    (catch Exception e
      (log/error "Failed to send test message:" (.getMessage e))
      (throw e))))

(defn fresh-kafka
  "Start a fresh Kafka instance for testing"
  []
  (if use-existing-kafka
    (do
      (log/info "Using existing Kafka instance")
      (if (kafka-running? "localhost" kafka-port)
        {:container nil
         :bootstrap-servers "localhost:9092"}
        (throw (Exception. "USE_EXISTING_KAFKA is set but Kafka is not running on localhost:9092"))))
    (let [container (start-kafka-container)]
      {:container container
       :bootstrap-servers (get-kafka-bootstrap-servers container)})))

(defn cleanup-kafka
  "Clean up Kafka test environment"
  [kafka-env]
  (when-let [container (:container kafka-env)]
    (try
      (log/info "Stopping Kafka container...")
      (.stop container)
      (log/info "Kafka container stopped")
      (catch Exception e
        (log/warn "Error stopping Kafka container:" (.getMessage e))))))

(defn with-kafka-env
  "Execute function with fresh Kafka environment"
  [f]
  (let [kafka-env (fresh-kafka)]
    (try
      (f kafka-env)
      (finally
        (cleanup-kafka kafka-env)))))

(defn create-test-setup
  "Create complete test setup with topic and producer"
  [kafka-env topic-name]
  (let [bootstrap-servers (:bootstrap-servers kafka-env)
        admin-client (create-kafka-admin-client bootstrap-servers)
        producer (create-kafka-producer bootstrap-servers)]
    (try
      (create-test-topic admin-client topic-name)
      {:admin-client admin-client
       :producer producer
       :topic topic-name
       :bootstrap-servers bootstrap-servers}
      (catch Exception e
        (.close admin-client)
        (.close producer)
        (throw e)))))

(defn cleanup-test-setup
  "Clean up test setup"
  [test-setup]
  (when-let [admin-client (:admin-client test-setup)]
    (try
      (.close admin-client)
      (catch Exception e
        (log/warn "Error closing admin client:" (.getMessage e)))))
  (when-let [producer (:producer test-setup)]
    (try
      (.close producer)
      (catch Exception e
        (log/warn "Error closing producer:" (.getMessage e)))))) 