(ns pebbles.sqs.sqs-test-utils
  (:require
   [cheshire.core :as json]
   [cognitect.aws.client.api :as aws]
   [pebbles.sqs.consumer :as sqs-consumer])
  (:import
   (org.testcontainers.containers.localstack LocalStackContainer LocalStackContainer$Service)
   (org.testcontainers.utility DockerImageName)
   (java.net Socket)))

;; Environment variable to use existing LocalStack instead of Testcontainers
(def use-existing-localstack (System/getenv "USE_EXISTING_LOCALSTACK"))

;; Default LocalStack port
(def localstack-port 4566)

(defn localstack-running?
  "Check if LocalStack is running locally on default port"
  [host port]
  (try
    (with-open [socket (Socket. host port)]
      (.isConnected socket))
    (catch Exception _ false)))

(defn start-localstack-container []
  (let [container (-> (LocalStackContainer. (DockerImageName/parse "localstack/localstack:3.4.0"))
                      (.withServices (into-array LocalStackContainer$Service [LocalStackContainer$Service/SQS]))
                      (.withEnv "HOSTNAME_EXTERNAL" "localhost"))]
    (println "Starting LocalStack container (this may take 30-60 seconds)...")
    (try
      (.start container)
      (println "LocalStack container started successfully")
      ;; Give it a moment to fully initialize SQS service
      (Thread/sleep 3000)
      container
      (catch Exception e
        (println "Failed to start LocalStack container:" (.getMessage e))
        (throw e)))))

(defn get-localstack-endpoint [container]
  (if container
    (let [host (.getHost container)
          port (.getMappedPort container localstack-port)]
      {:hostname host
       :port port
       :endpoint-url (str "http://" host ":" port)})
    {:hostname "localhost"
     :port localstack-port
     :endpoint-url (str "http://localhost:" localstack-port)}))

(defn create-test-queue
  "Create a test queue in LocalStack/SQS and return queue URL"
  [sqs-client queue-name]
  (println "Creating test queue:" queue-name)
  (let [create-result (aws/invoke sqs-client {:op :CreateQueue
                                             :request {:QueueName queue-name}})
        queue-url (:QueueUrl create-result)]
    (println "Create queue result:" create-result)
    (when-not queue-url
      (throw (ex-info "Failed to create test queue" {:result create-result})))
    (println "Queue created successfully:" queue-url)
    queue-url))

(defn send-test-message
  "Send a test progress update message to SQS queue"
  [sqs-client queue-url message-body]
  (aws/invoke sqs-client {:op :SendMessage
                         :request {:QueueUrl queue-url
                                  :MessageBody (json/generate-string message-body)}}))

(defn fresh-sqs "Set up fresh SQS environment for testing" [] 
  (let [test-credentials {:aws/access-key-id "test"
                         :aws/secret-access-key "test"}]
    (if (or use-existing-localstack (localstack-running? "localhost" localstack-port))
      ;; Use existing LocalStack connection
      (try
        (println "Using existing LocalStack on localhost:4566")
        (let [endpoint {:hostname "localhost" :port 4566 :endpoint-url "http://localhost:4566"}
              sqs-client (sqs-consumer/create-sqs-client 
                         :endpoint-override endpoint 
                         :region "us-east-1"
                         :credentials test-credentials)]
          (println "Successfully connected to existing LocalStack")
          {:sqs-client sqs-client :container nil :endpoint endpoint})
        (catch Exception e
          (println "Warning: Failed to connect to local LocalStack, falling back to testcontainer:" (.getMessage e))
          ;; Fall back to testcontainers if local connection fails
          (let [container (start-localstack-container)
                endpoint (get-localstack-endpoint container)
                sqs-client (sqs-consumer/create-sqs-client 
                           :endpoint-override endpoint 
                           :region "us-east-1"
                           :credentials test-credentials)]
            (println "Testcontainer started, endpoint:" endpoint)
            {:sqs-client sqs-client :container container :endpoint endpoint})))
      ;; Use Testcontainers
      (do
        (println "No existing LocalStack found, starting testcontainer...")
        (let [container (start-localstack-container)
              endpoint (get-localstack-endpoint container)
              sqs-client (sqs-consumer/create-sqs-client 
                         :endpoint-override endpoint 
                         :region "us-east-1"
                         :credentials test-credentials)]
          (println "Testcontainer started, endpoint:" endpoint)
          {:sqs-client sqs-client :container container :endpoint endpoint})))))

(defn cleanup-sqs "Clean up SQS test environment"
  [sqs-map] 
  (let [{:keys [container]} sqs-map]
    (when container (.stop container))))

