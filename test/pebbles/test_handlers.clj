(ns pebbles.test-handlers
  "Test-specific handlers that use mock-db"
  (:require
   [pebbles.mock-db :as db]
   [pebbles.http-resp :as resp]
   [clojure.tools.logging :as log]))

(defn update-progress-handler [db-atom]
  (fn [request]
    (let [email (get-in request [:identity :email])
          client-krn (get-in request [:path-params :client-krn])
          params (:json-params request)]
      (try
        (cond
          (not email)
          (resp/forbidden "No email found in authentication token")
          
          (not client-krn)
          (resp/bad-request "Client KRN is required")
          
          (not (:filename params))
          (resp/bad-request "Filename is required")
          
          :else
          (let [filename (:filename params)
                counts (:counts params)
                errors (:errors params)
                warnings (:warnings params)
                total (:total params)
                is-last (:isLast params)
                existing (db/find-progress-by-filename db-atom client-krn filename)]
            
            (cond
              ;; New progress
              (nil? existing)
              (let [progress-data {:clientKrn client-krn
                                  :filename filename
                                  :email email
                                  :counts counts
                                  :isCompleted (boolean is-last)
                                  :createdAt (str (java.time.Instant/now))}
                    progress-data (cond-> progress-data
                                    total (assoc :total total)
                                    errors (assoc :errors errors)
                                    warnings (assoc :warnings warnings))
                    result (db/create-progress db-atom progress-data)]
                (resp/ok (assoc result :result "created")))
              
              ;; Existing progress - check ownership
              (not= email (:email existing))
              (resp/forbidden "Only the original creator can update this file's progress")
              
              ;; Already completed
              (:isCompleted existing)
              (resp/bad-request "This file processing has already been completed")
              
              ;; Update existing
              :else
              (let [new-counts (merge-with + (:counts existing) counts)
                    new-errors (vec (concat (or (:errors existing) []) (or errors [])))
                    new-warnings (vec (concat (or (:warnings existing) []) (or warnings [])))
                    update-data {:counts new-counts
                                :isCompleted (boolean is-last)}
                    update-data (cond-> update-data
                                  total (assoc :total total)
                                  (seq new-errors) (assoc :errors new-errors)
                                  (seq new-warnings) (assoc :warnings new-warnings))
                    result (db/update-progress db-atom client-krn filename email {"$set" update-data})]
                (resp/ok (assoc result :result "updated"))))))
        
        (catch Exception e
          (log/error e "Error updating progress:" (.getMessage e))
          (resp/server-error (str "Database error: " (.getMessage e))))))))

(defn get-progress-handler [db-atom]
  (fn [request]
    (let [client-krn (get-in request [:path-params :client-krn])
          query-params (:query-params request)
          filename (:filename query-params)
          email (:email query-params)]
      (try
        (cond
          (not client-krn)
          (resp/bad-request "Client KRN is required")
          
          filename
          (if-let [progress (db/find-progress-by-filename db-atom client-krn filename)]
            (resp/ok (assoc progress :id (str (:_id progress))))
            (resp/not-found "Progress not found for this file"))
          
          email
          (let [progress-list (db/find-all-progress db-atom client-krn email)
                formatted (map #(assoc % :id (str (:_id %))) progress-list)]
            (resp/ok formatted))
          
          :else
          (let [progress-list (db/find-all-progress-by-client db-atom client-krn)
                formatted (map #(assoc % :id (str (:_id %))) progress-list)]
            (resp/ok formatted)))
        
        (catch Exception e
          (log/error e "Error getting progress:" (.getMessage e))
          (resp/server-error (str "Database error: " (.getMessage e))))))))