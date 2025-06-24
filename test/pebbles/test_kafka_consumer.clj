(ns pebbles.test-kafka-consumer
  "Test version of Kafka consumer that uses mock-db"
  (:require
   [clojure.data.json :as json]
   [clojure.spec.alpha :as s]
   [clojure.tools.logging :as log]
   [pebbles.mock-db :as db]
   [pebbles.specs :as specs]))

(defn parse-record
  "Parse Kafka record value to extract progress data"
  [record]
  (try
    (-> record
        .value
        (json/read-str :key-fn keyword))
    (catch Exception e
      (log/error "Failed to parse Kafka record:" e)
      nil)))

(defn validate-record
  "Validate Kafka record contains required fields"
  [record-data]
  (and (s/valid? ::specs/clientKrn (:clientKrn record-data))
       (s/valid? ::specs/email (:email record-data))
       (s/valid? ::specs/filename (:filename record-data))
       (s/valid? ::specs/counts (:counts record-data))))

(defn process-progress-update
  "Process a progress update from Kafka record"
  [db record-data]
  (let [{:keys [clientKrn email filename counts total isLast errors warnings]} record-data
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

(defn process-record
  "Process a single Kafka record"
  [db record]
  (if-let [record-data (parse-record record)]
    (if (validate-record record-data)
      (do
        (log/info "Processing Kafka record for" (:clientKrn record-data) "/" (:filename record-data))
        (let [result (process-progress-update db record-data)]
          (if (:error result)
            (log/error "Failed to process record:" (:error result))
            (log/info "Successfully processed record:" (:result result)))
          result))
      (do
        (log/error "Invalid record format:" record-data)
        {:error "Invalid record format"}))
    {:error "Failed to parse record"}))