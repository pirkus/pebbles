(ns pebbles.sqs-consumer
  (:require [clojure.core.async :as async]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [clojure.spec.alpha :as s]
            [com.stuartsierra.component :as component]
            [pebbles.specs :as specs]
            [pebbles.db :as db])
  (:import [software.amazon.awssdk.services.sqs SqsClient]
           [software.amazon.awssdk.services.sqs.model ReceiveMessageRequest DeleteMessageRequest]
           [software.amazon.awssdk.regions Region]))

(defn create-sqs-client
  "Create an SQS client"
  [region]
  (-> (SqsClient/builder)
      (.region (Region/of region))
      (.build)))

(defn parse-message
  "Parse SQS message body"
  [message]
  (try
    (json/read-str (.body message) :key-fn keyword)
    (catch Exception e
      (log/error e "Failed to parse SQS message")
      nil)))

(defn validate-message
  "Validate the message data has required fields"
  [message-data]
  (and (s/valid? ::specs/clientKrn (:clientKrn message-data))
       (s/valid? ::specs/email (:email message-data))
       (s/valid? ::specs/filename (:filename message-data))
       (s/valid? ::specs/counts (:counts message-data))))

(defn process-progress-update
  "Process a progress update from message data - same logic as HTTP endpoint"
  [db message-data]
  (let [{:keys [clientKrn email filename counts total isLast errors warnings]} message-data
        existing (db/find-progress db clientKrn filename email)]
    
    (cond
      ;; Check if already completed
      (and existing (:isCompleted existing))
      {:error "This file processing has already been completed"}
      
      ;; Check authorization
      (and existing (not= email (:email existing)))
      {:error "Only the original creator can update this file's progress"}
      
      ;; Create or update
      :else
      (let [timestamp (.toString (java.time.Instant/now))
            new-counts (if existing
                        (merge-with + (:counts existing) counts)
                        counts)
            doc (cond-> {:clientKrn clientKrn
                        :filename filename
                        :email email
                        :counts new-counts
                        :isCompleted (boolean isLast)}
                  (not existing) (assoc :createdAt timestamp)
                  total (assoc :total total)
                  errors (assoc :errors (concat (or (:errors existing) []) errors))
                  warnings (assoc :warnings (concat (or (:warnings existing) []) warnings)))]
        
        (if existing
          (do
            (db/update-progress db clientKrn filename email
                               {"$set" (dissoc doc :clientKrn :filename :email)})
            {:result "updated" :progress doc})
          (do
            (db/create-progress db doc)
            {:result "created" :progress doc}))))))

(defn delete-message
  "Delete a message from SQS after processing"
  [sqs-client queue-url receipt-handle]
  (try
    (let [request (-> (DeleteMessageRequest/builder)
                     (.queueUrl queue-url)
                     (.receiptHandle receipt-handle)
                     (.build))]
      (.deleteMessage sqs-client request))
    (catch Exception e
      (log/error e "Failed to delete SQS message"))))

(defn process-message
  "Process a single SQS message"
  [db sqs-client queue-url message]
  (let [receipt-handle (.receiptHandle message)
        message-data (parse-message message)]
    
    (if (and message-data (validate-message message-data))
      (let [result (process-progress-update db message-data)]
        (when-not (:error result)
          (delete-message sqs-client queue-url receipt-handle))
        result)
      (do
        ;; Delete invalid messages to prevent reprocessing
        (delete-message sqs-client queue-url receipt-handle)
        (if message-data
          {:error "Invalid message format"}
          {:error "Failed to parse message"})))))

(defn poll-messages
  "Poll messages from SQS queue"
  [sqs-client queue-url]
  (try
    (let [request (-> (ReceiveMessageRequest/builder)
                     (.queueUrl queue-url)
                     (.maxNumberOfMessages (int 10))
                     (.waitTimeSeconds (int 20))  ; Long polling
                     (.build))
          response (.receiveMessage sqs-client request)]
      (.messages response))
    (catch Exception e
      (log/error e "Failed to poll SQS messages")
      [])))

(defrecord SQSConsumerComponent [db queue-url region running]
  component/Lifecycle
  (start [this]
    (if running
      this
      (do
        (log/info "Starting SQS consumer for queue:" queue-url)
        (let [sqs-client (create-sqs-client region)
              running-flag (atom true)
              consumer-thread
              (async/thread
                (while @running-flag
                  (try
                    (let [messages (poll-messages sqs-client queue-url)]
                      (doseq [message messages]
                        (let [result (process-message db sqs-client queue-url message)]
                          (if (:error result)
                            (log/warn "Failed to process SQS message:" (:error result))
                            (log/info "Processed SQS message:" (:result result))))))
                    (catch Exception e
                      (log/error e "Error in SQS consumer loop")))))]
          (assoc this
                 :running running-flag
                 :sqs-client sqs-client
                 :consumer-thread consumer-thread)))))
  
  (stop [this]
    (if-not running
      this
      (do
        (log/info "Stopping SQS consumer")
        (reset! running false)
        (dissoc this :running :sqs-client :consumer-thread)))))

(defn sqs-consumer
  "Create an SQS consumer component"
  [queue-url region]
  (map->SQSConsumerComponent {:queue-url queue-url
                             :region region}))