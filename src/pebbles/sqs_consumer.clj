(ns pebbles.sqs-consumer
  (:require
   [clojure.core.async :as async]
   [clojure.data.json :as json]
   [clojure.spec.alpha :as s]
   [clojure.tools.logging :as log]
   [cognitect.aws.client.api :as aws]
   [com.stuartsierra.component :as component]
   [pebbles.db :as db]
   [pebbles.specs :as specs]))

(defn parse-message
  "Parse SQS message body to extract progress data"
  [message]
  (try
    (-> message
        :Body
        (json/read-str :key-fn keyword))
    (catch Exception e
      (log/error "Failed to parse SQS message:" e)
      nil)))

(defn validate-message
  "Validate SQS message contains required fields"
  [message-data]
  (and (s/valid? ::specs/clientKrn (:clientKrn message-data))
       (s/valid? ::specs/email (:email message-data))
       (s/valid? ::specs/progress-update-params message-data)))

(defn process-progress-update
  "Process a progress update from SQS message"
  [db message-data]
  (let [{:keys [clientKrn email filename counts total isLast errors warnings]} message-data
        {:keys [done warn failed]} counts
        now (.toString (java.time.Instant/now))
        
        ;; Find existing progress
        any-existing (db/find-progress-by-filename db clientKrn filename)
        existing (db/find-progress db clientKrn filename email)]
    
    (cond
      ;; Progress exists but user is not the creator
      (and any-existing (not= email (:email any-existing)))
      {:error "Only the original creator can update this file's progress"}
      
      ;; Progress already completed
      (and existing (:isCompleted existing))
      {:error "This file processing has already been completed"}
      
      ;; No existing progress - create new
      (nil? existing)
      (let [new-progress {:clientKrn clientKrn
                         :filename filename
                         :email email
                         :counts counts
                         :total total
                         :isCompleted (boolean isLast)
                         :createdAt now
                         :updatedAt now}
            new-progress (cond-> new-progress
                          errors (assoc :errors errors)
                          warnings (assoc :warnings warnings))]
        (db/create-progress db new-progress)
        {:result "created" :progress new-progress})
      
      ;; Update existing progress
      :else
      (let [new-counts {:done (+ (get-in existing [:counts :done] 0) done)
                       :warn (+ (get-in existing [:counts :warn] 0) warn)
                       :failed (+ (get-in existing [:counts :failed] 0) failed)}
            all-errors (concat (or (:errors existing) []) (or errors []))
            all-warnings (concat (or (:warnings existing) []) (or warnings []))
            update-doc {"$set" {:counts new-counts
                              :updatedAt now
                              :isCompleted (boolean isLast)
                              :errors all-errors
                              :warnings all-warnings}}
            update-doc (if (and total (nil? (:total existing)))
                        (assoc-in update-doc ["$set" :total] total)
                        update-doc)]
        
        (db/update-progress db clientKrn filename email update-doc)
        {:result "updated"
         :progress {:clientKrn clientKrn
                   :filename filename
                   :email email
                   :counts new-counts
                   :total (or total (:total existing))
                   :isCompleted (boolean isLast)
                   :errors all-errors
                   :warnings all-warnings}}))))

(defn process-message
  "Process a single SQS message"
  [db sqs queue-url message]
  (if-let [message-data (parse-message message)]
    (if (validate-message message-data)
      (do
        (log/info "Processing SQS message for" (:clientKrn message-data) "/" (:filename message-data))
        (let [result (process-progress-update db message-data)]
          (if (:error result)
            (log/error "Failed to process message:" (:error result))
            (do
              (log/info "Successfully processed message:" (:result result))
              ;; Delete message from queue on successful processing
              (aws/invoke sqs {:op :DeleteMessage
                             :request {:QueueUrl queue-url
                                     :ReceiptHandle (:ReceiptHandle message)}})))
          result))
      (do
        (log/error "Invalid message format:" message-data)
        ;; Delete invalid messages to prevent reprocessing
        (aws/invoke sqs {:op :DeleteMessage
                       :request {:QueueUrl queue-url
                               :ReceiptHandle (:ReceiptHandle message)}})
        {:error "Invalid message format"}))
    {:error "Failed to parse message"}))

(defn poll-messages
  "Poll SQS queue for messages"
  [sqs queue-url]
  (try
    (let [response (aws/invoke sqs {:op :ReceiveMessage
                                  :request {:QueueUrl queue-url
                                          :MaxNumberOfMessages 10
                                          :WaitTimeSeconds 20}})]
      (get response :Messages []))
    (catch Exception e
      (log/error "Failed to poll SQS:" e)
      [])))

(defrecord SqsConsumer [db queue-url region running? poll-chan]
  component/Lifecycle
  (start [this]
    (if running?
      this
      (let [sqs (aws/client {:api :sqs :region region})
            chan (async/chan)
            db-conn (:db db)]
        (log/info "Starting SQS consumer for queue:" queue-url)
        (async/go-loop []
          (when-let [_ (async/<! (async/timeout 100))]
            (when @running?
              (try
                (let [messages (poll-messages sqs queue-url)]
                  (doseq [message messages]
                    (process-message db-conn sqs queue-url message)))
                (catch Exception e
                  (log/error "Error in SQS consumer loop:" e)))
              (recur))))
        (assoc this 
               :running? (atom true)
               :poll-chan chan
               :sqs sqs))))
  
  (stop [this]
    (when running?
      (log/info "Stopping SQS consumer")
      (reset! running? false)
      (when poll-chan
        (async/close! poll-chan)))
    (assoc this :running? nil :poll-chan nil :sqs nil)))

(defn make-sqs-consumer
  "Create SQS consumer component"
  [{:keys [queue-url region]
    :or {region "us-east-1"}}]
  (map->SqsConsumer {:queue-url queue-url
                     :region region}))