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

;; Configure testcontainers to prevent hanging after tests
(defn configure-testcontainers!
  "Configure testcontainers to disable problematic shutdown hooks"
  []
  ;; Disable Ryuk entirely to prevent hanging after tests
  ;; We'll handle cleanup manually in our test fixtures
  (System/setProperty "testcontainers.ryuk.disabled" "true")
  
  ;; Enable container reuse for faster development  
  (System/setProperty "testcontainers.reuse.enable" "true")
  
  (log/info "Configured testcontainers: disabled Ryuk, enabled reuse - manual cleanup required"))

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
  ;; Configure testcontainers before starting any containers
  (configure-testcontainers!)
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
  (println "DEBUG: Starting topic creation for:" topic-name)
  (try
    (let [topic (NewTopic. topic-name 1 (short 1))]
      (println "DEBUG: Calling .createTopics...")
      (let [result (.createTopics admin-client (Collections/singletonList topic))]
        (println "DEBUG: .createTopics returned, waiting for completion...")
        ;; Wait for topic creation to complete (this was missing!)
        (.get (.all result))
        (println "DEBUG: Topic creation completed successfully")
        (log/info "Created test topic:" topic-name)
        topic-name))
    (catch Exception e
      (println "DEBUG: Exception in create-test-topic:" (.getMessage e))
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
  "Clean up Kafka test environment (manual cleanup since Ryuk is disabled)"
  [kafka-env]
  (println "DEBUG: Cleaning up Kafka test environment..." kafka-env)
  (let [container (:container kafka-env)]
    (println "DEBUG: Container:" container)
    (try
      (println "Stopping Kafka container manually...")
      
      ;; First try graceful stop
      (.stop container)
      (log/info "Kafka container stopped gracefully")
      
      ;; Force cleanup to ensure container is removed
      (Thread/sleep 1000)
      (when (.isRunning container)
        (log/warn "Container still running, forcing stop...")
        (.kill container))
      
      ;; Additional cleanup
      (try
        (.close container)
        (catch Exception e
          (log/debug "Container already closed:" (.getMessage e))))
      
      (log/info "Kafka container cleanup completed")
      (catch Exception e
        (log/warn "Error during Kafka container cleanup:" (.getMessage e))))))

(defn force-cleanup
  "Force cleanup of all resources including daemon threads"
  []
  (try
    ;; Force garbage collection to clean up any remaining resources
    (System/gc)
    (Thread/sleep 1000)
    ;; List and interrupt any remaining threads
    (let [thread-group (.getThreadGroup (Thread/currentThread))
          threads (make-array Thread (.activeCount thread-group))]
      (.enumerate thread-group threads)
      (doseq [thread threads]
        (when (and thread 
                   (.isAlive thread)
                   (.isDaemon thread)
                   (or (.contains (.getName thread) "kafka")
                       (.contains (.getName thread) "testcontainers")))
          (log/debug "Interrupting daemon thread:" (.getName thread))
          (.interrupt thread))))
    (catch Exception e
      (log/warn "Error during force cleanup:" (.getMessage e)))))

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
  (println "DEBUG: Creating test setup for topic:" topic-name)
  (let [bootstrap-servers (:bootstrap-servers kafka-env)]
    (println "DEBUG: Bootstrap servers:" bootstrap-servers)
    (println "DEBUG: Creating admin client...")
    (let [admin-client (create-kafka-admin-client bootstrap-servers)]
      (println "DEBUG: Admin client created, creating producer...")
      (let [producer (create-kafka-producer bootstrap-servers)]
        (println "DEBUG: Producer created, creating topic...")
        (try
          (create-test-topic admin-client topic-name)
          (println "DEBUG: Topic creation completed, returning test setup")
          {:admin-client admin-client
           :producer producer
           :topic topic-name
           :bootstrap-servers bootstrap-servers}
          (catch Exception e
            (println "DEBUG: Error in create-test-setup:" (.getMessage e))
            (.close admin-client)
            (.close producer)
            (throw e)))))))

(defn safe-close-consumer
  "Safely close a Kafka consumer with timeout"
  [consumer]
  (when consumer
    (try
      (log/debug "Closing Kafka consumer...")
      (.close consumer (java.time.Duration/ofSeconds 5))
      (log/debug "Kafka consumer closed")
      (catch Exception e
        (log/warn "Error closing consumer, forcing close:" (.getMessage e))
        (try
          (.close consumer)
          (catch Exception e2
            (log/warn "Failed to force close consumer:" (.getMessage e2))))))))

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