(ns pebbles.test-utils
  (:require
   [cheshire.core :as json]
   [monger.core :as mg]
   [org.testcontainers.containers :as tc])
  (:import
   (org.testcontainers.containers MongoDBContainer)
   (org.testcontainers.utility DockerImageName)))

(defn start-mongodb-container []
  (let [container (MongoDBContainer. 
                   (DockerImageName/parse "mongo:6.0"))]
    (.start container)
    container))

(defn get-connection-string [container]
  (.getReplicaSetUrl container))

(defn fresh-db []
  (let [container (start-mongodb-container)
        uri (get-connection-string container)
        {:keys [conn db]} (mg/connect-via-uri uri)]
    ;; Return db with container reference for cleanup
    (with-meta db {:container container :conn conn})))

(defn cleanup-db [db]
  (let [{:keys [container conn]} (meta db)]
    (when conn (mg/disconnect conn))
    (when container (.stop container))))

(defn make-test-request
  "Helper to create test requests with JSON params"
  [params & {:keys [identity headers] :or {headers {}}}]
  (cond-> {:json-params params
           :headers headers}
    identity (assoc :identity identity)))

(defn parse-json-response [response]
  (when-let [body (:body response)]
    (json/parse-string body true)))