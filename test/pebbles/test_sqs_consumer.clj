(ns pebbles.test-sqs-consumer
  "Test version of SQS consumer that uses mock-db"
  (:require [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [clojure.spec.alpha :as s]
            [pebbles.specs :as specs]
            [pebbles.mock-db :as db]))

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
  "Process a progress update from message data"
  [db message-data]
  (let [{:keys [clientKrn email filename counts total isLast errors warnings]} message-data
        any-existing (db/find-progress-by-filename db clientKrn filename)
        existing (db/find-progress db clientKrn filename email)]
    
    (cond
      ;; Check authorization - if file exists but user is not the creator
      (and any-existing (not= email (:email any-existing)))
      {:error "Only the original creator can update this file's progress"}
      
      ;; Check if already completed
      (and existing (:isCompleted existing))
      {:error "This file processing has already been completed"}
      
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

(defn process-message
  "Process a single SQS message"
  [db sqs-client queue-url message]
  (let [receipt-handle (.receiptHandle message)
        message-data (parse-message message)]
    
    (if (and message-data (validate-message message-data))
      (let [result (process-progress-update db message-data)]
        ;; Always delete message after processing
        (when sqs-client
          (.deleteMessage sqs-client
                         (-> (software.amazon.awssdk.services.sqs.model.DeleteMessageRequest/builder)
                             (.queueUrl queue-url)
                             (.receiptHandle receipt-handle)
                             (.build))))
        result)
      (do
        ;; Delete invalid messages to prevent reprocessing
        (when sqs-client
          (.deleteMessage sqs-client
                         (-> (software.amazon.awssdk.services.sqs.model.DeleteMessageRequest/builder)
                             (.queueUrl queue-url)
                             (.receiptHandle receipt-handle)
                             (.build))))
        (if message-data
          {:error "Invalid message format"}
          {:error "Failed to parse message"})))))