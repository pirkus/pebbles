(ns pebbles.test-utils
  (:require
   [cheshire.core :as json]
   [monger.core :as mg]
   [monger.db :as mdb]
   [monger.collection :as mc])
  (:import
   (org.testcontainers.containers MongoDBContainer)
   (org.testcontainers.utility DockerImageName)))

;; Environment variable to use existing MongoDB instead of Testcontainers
(def use-existing-mongo (System/getenv "USE_EXISTING_MONGO"))

(defn start-mongodb-container []
  (let [container (MongoDBContainer.
                   (DockerImageName/parse "mongo:6.0"))]
    (.start container)
    container))

(defn get-connection-string [container]
  (.getReplicaSetUrl container))

(defn fresh-db []
  (if use-existing-mongo
    ;; Use existing MongoDB connection
    (let [uri (or (System/getenv "MONGO_URI") "mongodb://localhost:27017/test")
          {:keys [conn db]} (mg/connect-via-uri uri)]
      ;; Clear all collections in the test database
      (doseq [coll-name (mdb/get-collection-names db)]
        (mc/drop db coll-name))
      {:db db :container nil :conn conn})
    ;; Use Testcontainers
    (let [container (start-mongodb-container)
          uri (get-connection-string container)
          {:keys [conn db]} (mg/connect-via-uri uri)]
      ;; Return a map with db and container reference for cleanup
      {:db db :container container :conn conn})))

(defn clear-collections
  "Clear all collections in the database" 
  [db] 
  (doseq [coll-name (mdb/get-collection-names db)]
    (mc/drop db coll-name)))

(defn reuse-db 
  "Reuse existing database connection, just clear collections"
  [existing-db-map] 
  (let [{:keys [db]} existing-db-map]
    (clear-collections db)
    existing-db-map))

(defn cleanup-db [db-map]
  (let [{:keys [container conn db]} db-map]
    (when use-existing-mongo
      ;; Clear all collections after test
      (clear-collections db))
    (when conn (mg/disconnect conn))
    (when container (.stop container))))

(defn make-test-request
  "Helper to create test requests with JSON params"
  [params & {:keys [identity headers path-params] :or {headers {}}}]
  (cond-> {:json-params params
           :headers headers}
    identity (assoc :identity identity)
    path-params (assoc :path-params path-params)))

(defn parse-json-response [response]
  (when-let [body (:body response)]
    (json/parse-string body true)))