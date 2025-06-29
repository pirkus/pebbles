(ns pebbles.sqs.consumer
  (:require
   [clojure.tools.logging :as log]
   [cheshire.core :as json]
   [cognitect.aws.client.api :as aws]
   [cognitect.aws.credentials :as credentials]
   [com.stuartsierra.component :as component]
   [clojure.spec.alpha :as s]
   [pebbles.handlers :as handlers]
   [pebbles.specs :as specs]
   [pebbles.db :as db]))

;; ----------------------------------------------------------------------------
;; SQS Client Configuration
;; ----------------------------------------------------------------------------

(defn create-sqs-client
  "Create SQS client with optional endpoint override for LocalStack"
  [& {:keys [endpoint-override region credentials]}]
  (let [config (cond-> {:api :sqs}
                 region (assoc :region region)
                 credentials (assoc :credentials-provider 
                                   (reify credentials/CredentialsProvider
                                     (fetch [_] credentials)))
                 endpoint-override (assoc :endpoint-override 
                                         {:protocol :http
                                          :hostname (:hostname endpoint-override)
                                          :port (:port endpoint-override)}))]
    (aws/client config)))

;; ----------------------------------------------------------------------------
;; Message Processing
;; ----------------------------------------------------------------------------

(defn parse-sqs-message
  "Parse SQS message body and extract progress update data"
  [message]
  (try
    (let [body (:Body message)
          parsed (json/parse-string body true)]
      ;; Validate using spec
      (if (s/valid? ::specs/sqs-progress-message parsed)
        parsed
        (let [explain-data (s/explain-data ::specs/sqs-progress-message parsed)
              error-msg (str "Invalid SQS message format: " (s/explain-str ::specs/sqs-progress-message parsed))]
          (throw (ex-info error-msg {:parsed parsed :explain-data explain-data})))))
    (catch Exception e
      (log/error "Failed to parse SQS message" {:message message :error (.getMessage e)})
      (throw e))))

(defn process-progress-message
  "Process a single progress update message using the same logic as HTTP handler"
  [db message]
  (try
    (let [{:keys [clientKrn email filename counts total isLast errors warnings]} (parse-sqs-message message)
          now (.toString (java.time.Instant/now))
          any-existing (db/find-progress-by-filename db clientKrn filename)
          existing (db/find-progress db clientKrn filename email)
          validation-error (handlers/validate-progress-update-request email clientKrn any-existing existing)]
      
      (if validation-error
        (do
          (log/warn "Validation failed for SQS message" 
                   {:clientKrn clientKrn :filename filename :email email :error (:body validation-error)})
          {:status :validation-error :error (:body validation-error)})
        (let [response (if (nil? existing)
                        (handlers/create-new-progress db clientKrn filename email counts total isLast errors warnings now)
                        (handlers/update-existing-progress db clientKrn filename email existing counts total isLast errors warnings now))
              result (json/parse-string (:body response) true)]
          (log/info "Successfully processed SQS progress update" 
                   {:clientKrn clientKrn :filename filename :email email :result (:result result)})
          {:status :success :result result})))
    (catch Exception e
      (log/error "Error processing SQS progress message" {:message message :error (.getMessage e)})
      {:status :error :error (.getMessage e)})))

;; ----------------------------------------------------------------------------
;; SQS Consumer Component
;; ----------------------------------------------------------------------------

(defrecord SqsConsumerComponent [queue-url region endpoint-override polling-enabled credentials mongo sqs-client consumer-thread]
  component/Lifecycle
  
  (start [this]
    (if consumer-thread
      this
      (let [client (create-sqs-client :region region :endpoint-override endpoint-override :credentials credentials)
            db (:db mongo)
            running (atom true)
            thread (Thread.
                    (fn []
                      (log/info "Starting SQS consumer" {:queue-url queue-url})
                      (while @running
                        (try
                          (when polling-enabled
                            (let [receive-result (aws/invoke client {:op :ReceiveMessage
                                                                    :request {:QueueUrl queue-url
                                                                             :MaxNumberOfMessages 10
                                                                             :WaitTimeSeconds 20}})
                                  messages (:Messages receive-result)]
                              (when (seq messages)
                                (log/debug "Received SQS messages" {:count (count messages)})
                                (doseq [message messages]
                                  (let [result (process-progress-message db message)]
                                    (when (= :success (:status result))
                                      ;; Delete message only on successful processing
                                      (aws/invoke client {:op :DeleteMessage
                                                        :request {:QueueUrl queue-url
                                                                 :ReceiptHandle (:ReceiptHandle message)}})))))))
                          (catch InterruptedException _
                            (log/info "SQS consumer interrupted")
                            (reset! running false))
                          (catch Exception e
                            (log/error "Error in SQS consumer loop" {:error (.getMessage e)})
                            (Thread/sleep 5000)))))
                    "sqs-consumer")]
        (.start thread)
        (assoc this :sqs-client client :consumer-thread thread))))
  
  (stop [this]
    (when consumer-thread
      (log/info "Stopping SQS consumer")
      (.interrupt consumer-thread)
      (try
        (.join consumer-thread 5000)
        (catch InterruptedException _
          (log/warn "Timeout waiting for SQS consumer thread to stop"))))
    (assoc this :sqs-client nil :consumer-thread nil)))

;; ----------------------------------------------------------------------------
;; Component Factory
;; ----------------------------------------------------------------------------

(defn make-sqs-consumer
  "Create SQS consumer component with configuration"
  [& {:keys [queue-url region endpoint-override polling-enabled credentials]
      :or {region "us-east-1" polling-enabled true}}]
  (map->SqsConsumerComponent {:queue-url queue-url
                             :region region
                             :endpoint-override endpoint-override
                             :polling-enabled polling-enabled
                             :credentials credentials})) 