(ns pebbles.http-resp
  (:require [ring.util.response :as response]
            [cheshire.core :as json])
  (:import [java.security MessageDigest]
           [java.nio.charset StandardCharsets]))

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

;; ----------------------------------------------------------------------------
;; ETag Utilities
;; ----------------------------------------------------------------------------

(defn generate-etag
  "Generate an ETag from response data using MD5 hash"
  [data]
  (let [json-str (json/generate-string data)
        digest (MessageDigest/getInstance "MD5")
        hash-bytes (.digest digest (.getBytes json-str StandardCharsets/UTF_8))
        hex-hash (apply str (map #(format "%02x" (bit-and % 0xff)) hash-bytes))]
    (str "\"" hex-hash "\"")))

(defn matches-etag?
  "Check if the request's If-None-Match header matches the generated ETag"
  [request etag]
  (when-let [if-none-match (get-in request [:headers "if-none-match"])]
    (or (= if-none-match "*")
        (= if-none-match etag))))

(defn not-modified
  "Return 304 Not Modified response with ETag"
  [etag]
  {:status 304
   :headers {"ETag" etag
             "Cache-Control" "no-cache"}
   :body ""})

(defn ok-with-etag
  "Return 200 OK response with ETag header"
  [data etag]
  {:status 200
   :headers {"Content-Type" "application/json; charset=utf-8"
             "ETag" etag
             "Cache-Control" "no-cache"}
   :body (json/generate-string data)})

(defn conditional-response
  "Return either 304 Not Modified or 200 OK with ETag based on request headers"
  [request data]
  (let [etag (generate-etag data)]
    (if (matches-etag? request etag)
      (not-modified etag)
      (ok-with-etag data etag))))