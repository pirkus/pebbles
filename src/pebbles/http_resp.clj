(ns pebbles.http-resp
  (:require [ring.util.response :as response]
            [cheshire.core :as json]))

(defn json-response
  [status body]
  (-> (response/response (json/generate-string body))
      (response/status status)
      (response/content-type "application/json")))

(defn ok [body]
  (json-response 200 body))

(defn bad-request [message]
  (json-response 400 {:error message}))

(defn not-found [message]
  (json-response 404 {:error message}))

(defn forbidden [message]
  (json-response 403 {:error message}))

(defn server-error [message]
  (json-response 500 {:error message}))

(defn handle-validation-error [ex]
  (bad-request (str "Validation error: " (.getMessage ex))))

(defn handle-db-error [ex]
  (if (instance? com.mongodb.DuplicateKeyException ex)
    (bad-request "Duplicate key error")
    (server-error (str "Database error: " (.getMessage ex)))))