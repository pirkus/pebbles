(ns pebbles.db
  (:require
   [monger.collection :as mc]))

(defn consolidate-messages
  "Consolidate errors or warnings by message, grouping line numbers into arrays"
  [items]
  (->> items
       (group-by :message)
       (map (fn [[message items]]
              {:message message
               :lines (->> items
                          (mapcat #(if (:lines %) (:lines %) [(:line %)]))
                          vec)}))
       vec))

(defn prepare-progress-data
  "Prepare progress data by consolidating duplicate error/warning messages"
  [progress-data]
  (cond-> progress-data
    (:errors progress-data) (update :errors consolidate-messages)
    (:warnings progress-data) (update :warnings consolidate-messages)))

(defn find-progress
  "Find progress document by clientKrn, filename and email"
  [db client-krn filename email]
  (mc/find-one-as-map db "progress" {:clientKrn client-krn
                                     :filename filename 
                                     :email email}))

(defn find-progress-by-filename
  "Find progress document by clientKrn and filename only (used for authorization checks)"
  [db client-krn filename]
  (mc/find-one-as-map db "progress" {:clientKrn client-krn 
                                     :filename filename}))

(defn create-progress
  "Create a new progress document"
  [db progress-data]
  (let [prepared-data (prepare-progress-data progress-data)]
    (mc/insert-and-return db "progress" prepared-data)))

(defn update-progress
  "Update existing progress document and return the updated document"
  [db client-krn filename email update-data]
  (mc/find-and-modify db "progress" 
                      {:clientKrn client-krn
                       :filename filename 
                       :email email}
                      update-data
                      {:return-new true}))

(defn find-all-progress
  "Find all progress documents for a user within a client"
  [db client-krn email]
  (mc/find-maps db "progress" {:clientKrn client-krn 
                               :email email}))

(defn find-all-progress-for-client
  "Find all progress documents for a client"
  [db client-krn]
  (mc/find-maps db "progress" {:clientKrn client-krn}))