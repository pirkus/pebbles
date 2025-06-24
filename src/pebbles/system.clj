(ns pebbles.system
  (:require
   [clojure.spec.alpha :as s]
   [clojure.tools.logging :as log]
   [com.stuartsierra.component :as component]
   [io.pedestal.http :as http]
   [io.pedestal.http.body-params :refer [body-params]]
   [io.pedestal.http.route :as route]
   [io.pedestal.interceptor :as interceptor]
   [io.pedestal.interceptor.error :as err]
   [monger.collection :as mc]
   [monger.core :as mg]
   [pebbles.db :as db]
   [pebbles.http-resp :as http-resp]
   [pebbles.jwt :as jwt]
   [pebbles.specs :as specs]))

(def exception-handler
  (err/error-dispatch [context ex]
    [{:exception-type :com.fasterxml.jackson.core.io.JsonEOFException}]
    (do
      (log/warn "JSON parsing error:" ex)
      (assoc context :response (http-resp/handle-validation-error ex)))

    [{:exception-type :com.mongodb.MongoException}]
    (do
      (log/error "MongoDB error:" ex)
      (assoc context :response (http-resp/handle-db-error ex)))

    :else
    (do
      (log/error "Unhandled exception:" ex)
      (assoc context :response (http-resp/server-error (str ex))))))

;; ----------------------------------------------------------------------------
;; Mongo Component
;; ----------------------------------------------------------------------------

(defrecord MongoComponent [uri conn db]
  component/Lifecycle
  (start [this]
    (let [{:keys [conn db]} (mg/connect-via-uri uri)]
      ;; Create compound index for clientKrn + filename + email (unique per client and user)
      (mc/ensure-index db "progress" (array-map :clientKrn 1 :filename 1 :email 1) {:unique true})
      ;; Index for faster queries by clientKrn + email
      (mc/ensure-index db "progress" (array-map :clientKrn 1 :email 1) {:name "progress_client_email_idx"})
      ;; Index for faster queries by clientKrn only
      (mc/ensure-index db "progress" (array-map :clientKrn 1) {:name "progress_client_idx"})
      (assoc this :conn conn :db db)))
  (stop [this]
    (when conn (mg/disconnect conn))
    (assoc this :conn nil :db nil)))

;; ----------------------------------------------------------------------------
;; Validation Interceptor
;; ----------------------------------------------------------------------------

(defn validate-progress-update []
  (interceptor/interceptor
   {:name ::validate-progress-update
    :enter (fn [context]
             (let [params (get-in context [:request :json-params])]
               (if (s/valid? ::specs/progress-update-params params)
                 context
                 (assoc context :response
                        (http-resp/bad-request 
                         (str "Invalid parameters: " 
                              (s/explain-str ::specs/progress-update-params params)))))))}))

;; ----------------------------------------------------------------------------
;; HTTP Handlers
;; ----------------------------------------------------------------------------

(defn update-progress-handler [db]
  (fn [request]
    (try
      (let [email (get-in request [:identity :email])
            client-krn (get-in request [:path-params :clientKrn])
            {:keys [filename counts total isLast errors warnings]} (:json-params request)
            {:keys [done warn failed]} counts
            now (.toString (java.time.Instant/now))
            
            ;; Find existing progress for authorization check
            any-existing (db/find-progress-by-filename db client-krn filename)
            ;; Find existing progress for the current user
            existing (db/find-progress db client-krn filename email)]
        
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
          
          ;; No existing progress - create new
          (nil? existing)
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
                _ (db/create-progress db new-progress)]
            (http-resp/ok {:result "created" 
                          :clientKrn client-krn
                          :filename filename
                          :counts counts
                          :total total
                          :isCompleted (boolean isLast)
                          :errors (or errors [])
                          :warnings (or warnings [])}))
          
          ;; Update existing progress
          :else
          (let [;; Calculate new counts by adding to existing
                new-counts {:done (+ (get-in existing [:counts :done] 0) done)
                           :warn (+ (get-in existing [:counts :warn] 0) warn)
                           :failed (+ (get-in existing [:counts :failed] 0) failed)}
                ;; Append new errors and warnings to existing ones
                all-errors (concat (or (:errors existing) []) (or errors []))
                all-warnings (concat (or (:warnings existing) []) (or warnings []))
                update-doc {"$set" {:counts new-counts
                                 :updatedAt now
                                 :isCompleted (boolean isLast)
                                 :errors all-errors
                                 :warnings all-warnings}}
                ;; Add total if provided and not already set
                update-doc (if (and total (nil? (:total existing)))
                            (assoc-in update-doc ["$set" :total] total)
                            update-doc)]
            
            (db/update-progress db client-krn filename email update-doc)
            (http-resp/ok {:result "updated"
                          :clientKrn client-krn
                          :filename filename
                          :counts new-counts
                          :total (or total (:total existing))
                          :isCompleted (boolean isLast)
                          :errors all-errors
                          :warnings all-warnings}))))
      
      (catch Exception e
        (log/error "Error updating progress:" e)
        (http-resp/handle-db-error e)))))

(defn get-progress-handler [db]
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
            (http-resp/ok (-> progress 
                             (dissoc :_id)
                             (assoc :id (str (:_id progress)))))
            (http-resp/not-found "Progress not found for this file"))
          
          ;; Get all progress for specific user by clientKrn + email
          email
          (let [user-progress (db/find-all-progress db client-krn email)]
            (http-resp/ok (->> user-progress
                              (map #(-> %
                                       (dissoc :_id)
                                       (assoc :id (str (:_id %)))))
                              (sort-by :updatedAt)
                              reverse)))
          
          ;; Get all progress for the client
          :else
          (let [client-progress (db/find-all-progress-for-client db client-krn)]
            (http-resp/ok (->> client-progress
                              (map #(-> %
                                       (dissoc :_id)
                                       (assoc :id (str (:_id %)))))
                              (sort-by :updatedAt)
                              reverse)))))
      
      (catch Exception e
        (log/error "Error getting progress:" e)
        (http-resp/handle-db-error e)))))

(defn health-handler []
  (fn [_]
    {:status 200
     :body   "OK"}))

;; ----------------------------------------------------------------------------
;; Routes
;; ----------------------------------------------------------------------------

(defn make-routes [db]
  (route/expand-routes
   #{["/progress/:clientKrn" :post
      [jwt/auth-interceptor exception-handler (body-params) (validate-progress-update) (update-progress-handler db)]
      :route-name :progress-update]

     ["/progress/:clientKrn" :get
      [exception-handler (get-progress-handler db)]
      :route-name :progress-get]

     ["/health" :get
      [(health-handler)]
      :route-name :health]}))

;; ----------------------------------------------------------------------------
;; HTTP Component
;; ----------------------------------------------------------------------------

(defn make-server [port routes]
  (-> {::http/routes routes
       ::http/type   :jetty
       ::http/host   "0.0.0.0"
       ::http/port   port
       ::http/allowed-origins {:creds true :allowed-origins (constantly true)}}
      http/create-server))

(defrecord HttpComponent [port mongo server]
  component/Lifecycle
  (start [this]
    (if server
      this
      (let [db (:db mongo)
            srv (make-server port (make-routes db))]
        (assoc this :server (http/start srv)))))
  (stop [this]
    (when server (http/stop server))
    (assoc this :server nil)))

;; ----------------------------------------------------------------------------
;; System assembly
;; ----------------------------------------------------------------------------

(defn system []
  (component/system-map
   :mongo (map->MongoComponent {:uri (or (System/getenv "MONGO_URI") "mongodb://localhost:27017/pebbles")})
   :http  (component/using
           (map->HttpComponent {:port (Integer/parseInt (or (System/getenv "PORT") "8081"))})
           [:mongo])))

(defn -main [& _]
  (component/start (system)))