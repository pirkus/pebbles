(ns pebbles.handlers
  (:require
   [clojure.tools.logging :as log]
   [pebbles.db :as db]
   [pebbles.http-resp :as http-resp]
   [pebbles.statistical-grouping :as statistical-grouping]
   [pebbles.openapi :as openapi]))

;; ----------------------------------------------------------------------------
;; Validation Functions
;; ----------------------------------------------------------------------------

(defn validate-progress-update-request
  "Validates the progress update request and returns an error response or nil"
  [email client-krn any-existing existing]
  (cond
    ;; No email from JWT
    (nil? email)
    (http-resp/forbidden "No email found in authentication token")
    
    ;; No clientKrn in path
    (nil? client-krn)
    (http-resp/bad-request "clientKrn path parameter is required")
    
    ;; Progress exists but user is not the creator - reject with 403
    (and any-existing (not= email (:email any-existing)))
    (http-resp/forbidden "Only the original creator can update this file's progress")
    
    ;; Progress already completed
    (and existing (:isCompleted existing))
    (http-resp/bad-request "This file processing has already been completed")
    
    :else nil))

;; ----------------------------------------------------------------------------
;; Helper Functions
;; ----------------------------------------------------------------------------

(defn format-progress-response
  "Formats a progress document for the API response"
  [progress]
  (-> progress 
      (dissoc :_id)
      (assoc :id (str (:_id progress)))))

(defn format-progress-list
  "Formats a list of progress documents, sorted by most recent first"
  [progress-list]
  (->> progress-list
       (map format-progress-response)
       (sort-by :updatedAt)
       reverse))

;; ----------------------------------------------------------------------------
;; Progress Management Functions
;; ----------------------------------------------------------------------------

(defn create-new-progress
  "Creates a new progress record"
  [db client-krn filename email counts total isLast errors warnings now]
  (let [new-progress {:clientKrn client-krn
                      :filename filename
                      :email email
                      :counts counts
                      :total total
                      :isCompleted (boolean isLast)
                      :createdAt now
                      :updatedAt now}
        ;; Add optional fields if present
        new-progress (cond-> new-progress
                       errors (assoc :errors errors)
                       warnings (assoc :warnings warnings))
        saved (db/create-progress db new-progress)]
    (http-resp/ok {:result "created" 
                   :clientKrn client-krn
                   :filename filename
                   :counts counts
                   :total total
                   :isCompleted (boolean isLast)
                   :errors (or (:errors saved) [])
                   :warnings (or (:warnings saved) [])})))

(defn update-existing-progress
  "Updates an existing progress record"
  [db client-krn filename email existing counts total isLast errors warnings now]
  (let [{:keys [done warn failed]} counts
        ;; Calculate new counts by adding to existing
        new-counts {:done (+ (get-in existing [:counts :done] 0) done)
                    :warn (+ (get-in existing [:counts :warn] 0) warn)
                    :failed (+ (get-in existing [:counts :failed] 0) failed)}
        ;; Use pattern-aware consolidation
        existing-error-groups (or (:errors existing) [])
        existing-warning-groups (or (:warnings existing) [])
        ;; Consolidate new errors with existing patterns
        consolidated-errors (when errors
                             (statistical-grouping/consolidate-with-existing-patterns
                              errors existing-error-groups))
        ;; Consolidate new warnings with existing patterns  
        consolidated-warnings (when warnings
                               (statistical-grouping/consolidate-with-existing-patterns
                                warnings existing-warning-groups))
        ;; Prepare final update data
        update-doc {"$set" {:counts new-counts
                           :total (or total (:total existing))
                           :isCompleted (boolean isLast)
                           :updatedAt now
                           :errors (or consolidated-errors existing-error-groups)
                           :warnings (or consolidated-warnings existing-warning-groups)}}
        
        updated (db/update-progress db client-krn filename email update-doc)]
    (http-resp/ok {:result "updated"
                   :clientKrn client-krn
                   :filename filename
                   :counts (:counts updated)
                   :total (:total updated)
                   :isCompleted (:isCompleted updated)
                   :errors (or (:errors updated) [])
                   :warnings (or (:warnings updated) [])})))

;; ----------------------------------------------------------------------------
;; HTTP Handler Functions
;; ----------------------------------------------------------------------------

(defn update-progress-handler [db]
  (with-meta
    (fn [request]
      (try
        (let [email (get-in request [:identity :email])
              client-krn (get-in request [:path-params :clientKrn])
              {:keys [filename counts total isLast errors warnings]} (:json-params request)
              now (.toString (java.time.Instant/now))
              any-existing (db/find-progress-by-filename db client-krn filename)
              existing (db/find-progress db client-krn filename email)
              validation-error (validate-progress-update-request email client-krn any-existing existing)]
          (if validation-error
            validation-error
            (if (nil? existing)
              (create-new-progress db client-krn filename email counts total isLast errors warnings now)
              (update-existing-progress db client-krn filename email existing counts total isLast errors warnings now))))
        
        (catch Exception e
          (log/error "Error updating progress:" e)
          (http-resp/handle-db-error e))))
    ;; OpenAPI metadata
    {:post {:summary "Update file processing progress"
            :description "Create or update progress for a file within a client tenant. Only authenticated users can update, and only the original creator can modify their file's progress."
            :tags ["progress"]
            :security [{:bearerAuth []}]
            :requestBody {:required true
                          :content {"application/json"
                                    {:schema (openapi/ref-schema "progress-update-params")}}}
            :responses {200 {:description "Progress created or updated successfully"
                             :content {"application/json"
                                       {:schema (openapi/ref-schema "progress-response")}}}}}}))

(defn get-progress-handler [db]
  (with-meta
    (fn [request]
      (try
        (let [client-krn (get-in request [:path-params :clientKrn])
              filename (get-in request [:query-params :filename])
              email (get-in request [:query-params :email])]

          (cond
            ;; No clientKrn provided
            (nil? client-krn)
            (http-resp/bad-request "clientKrn path parameter is required")

            ;; Get specific file progress by clientKrn + filename
            filename
            (if-let [progress (db/find-progress-by-filename db client-krn filename)]
              (http-resp/conditional-response request (format-progress-response progress))
              (http-resp/not-found "Progress not found for this file"))

            ;; Get all progress for specific user by clientKrn + email
            email
            (let [user-progress (db/find-all-progress db client-krn email)
                  formatted-progress (format-progress-list user-progress)]
              (http-resp/conditional-response request formatted-progress))

            ;; Get all progress for the client
            :else
            (let [client-progress (db/find-all-progress-for-client db client-krn)
                  formatted-progress (format-progress-list client-progress)]
              (http-resp/conditional-response request formatted-progress))))
      
      (catch Exception e
        (log/error "Error getting progress:" e)
        (http-resp/handle-db-error e))))
    ;; OpenAPI metadata
    {:get {:summary "Retrieve progress information"
           :description "Get progress for a specific client, file, or user. No authentication required, but clientKrn is mandatory for data isolation. Supports ETag-based conditional requests for efficient polling."
           :tags ["progress"]
           :parameters [(openapi/common-parameters :filename)
                        (openapi/common-parameters :email)]
           :responses {200 {:description "Progress data retrieved"
                            :content {"application/json"
                                      {:schema {:oneOf [(openapi/ref-schema "progress-record")
                                                        {:type "array"
                                                         :items (openapi/ref-schema "progress-record")}]}}}}
                       304 {:description "Not Modified - data hasn't changed since last request"}
                       404 {:description "Progress not found"}
                       400 {:description "Bad request - missing clientKrn"}}}}))

(defn health-handler []
  (with-meta
    (fn [_]
      {:status 200
       :body   "OK"})
    ;; OpenAPI metadata
    {:get {:summary "Health check endpoint"
           :tags ["health"]
           :responses {200 {:description "Service is healthy"
                            :content {"text/plain"
                                      {:schema {:type "string"
                                                :example "OK"}}}}}}})) 