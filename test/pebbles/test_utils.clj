(ns pebbles.test-utils
  (:require
   [cheshire.core :as json]))

;; Simple in-memory database mock using atoms
(defn create-mock-db []
  (atom {}))

(defn fresh-db []
  {:db (create-mock-db)
   :conn nil
   :mongo-instance nil})

(defn cleanup-db [db-map]
  ;; Nothing to clean up for in-memory mock
  nil)

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