(ns pebbles.system
  (:require
   [com.stuartsierra.component :as component]
   [io.pedestal.http :as http]
   [io.pedestal.http.body-params :refer [body-params]]
   [io.pedestal.http.route :as route]
   [monger.collection :as mc]
   [monger.core :as mg]
   [pebbles.handlers :as handlers]
   [pebbles.interceptors :as interceptors]
   [pebbles.jwt :as jwt]
   [pebbles.openapi-handlers :as openapi-handlers]
   [pebbles.sqs.consumer :as sqs-consumer]))



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
;; Routes
;; ----------------------------------------------------------------------------

(defn make-routes [db]
  (route/expand-routes
   #{["/progress/:clientKrn" :post
      [jwt/auth-interceptor interceptors/exception-handler (body-params) (interceptors/validate-progress-update) (handlers/update-progress-handler db)]
      :route-name :progress-update]

     ["/progress/:clientKrn" :get
      [interceptors/exception-handler (handlers/get-progress-handler db)]
      :route-name :progress-get]

     ["/health" :get
      [(handlers/health-handler)]
      :route-name :health]
     
     ["/openapi.json" :get
      [(openapi-handlers/openapi-json-handler db)]
      :route-name :openapi-spec]
     
     ["/api-docs" :get
      [(openapi-handlers/swagger-ui-handler)]
      :route-name :swagger-ui]}))

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
  (let [sqs-queue-url (System/getenv "SQS_QUEUE_URL")
        sqs-region (or (System/getenv "AWS_REGION") "us-east-1")
        sqs-endpoint (System/getenv "SQS_ENDPOINT")]
    (cond-> (component/system-map
             :mongo (map->MongoComponent {:uri (or (System/getenv "MONGO_URI") "mongodb://localhost:27017/pebbles")})
             :http  (component/using
                     (map->HttpComponent {:port (Integer/parseInt (or (System/getenv "PORT") "8081"))})
                     [:mongo]))
      ;; Add SQS consumer only if queue URL is configured
      sqs-queue-url (assoc :sqs-consumer
                          (component/using
                           (sqs-consumer/make-sqs-consumer
                            :queue-url sqs-queue-url
                            :region sqs-region
                            :endpoint-override (when sqs-endpoint
                                               (let [url (java.net.URL. sqs-endpoint)]
                                                 {:hostname (.getHost url)
                                                  :port (.getPort url)})))
                           [:mongo])))))

(defn -main [& _]
  (component/start (system)))