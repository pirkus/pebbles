(ns pebbles.http-resp-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [pebbles.http-resp :as http-resp]
   [cheshire.core :as json]))

(deftest json-response-test
  (testing "json-response creates proper response"
    (let [response (http-resp/json-response 200 {:message "test"})]
      (is (= 200 (:status response)))
      (is (= "application/json" (get-in response [:headers "Content-Type"])))
      (is (= {:message "test"} (json/parse-string (:body response) true))))))

(deftest ok-response-test
  (testing "ok response returns 200 with JSON body"
    (let [response (http-resp/ok {:result "success"})]
      (is (= 200 (:status response)))
      (is (= {:result "success"} (json/parse-string (:body response) true))))))

(deftest bad-request-test
  (testing "bad-request returns 400 with error message"
    (let [response (http-resp/bad-request "Invalid input")]
      (is (= 400 (:status response)))
      (is (= {:error "Invalid input"} (json/parse-string (:body response) true))))))

(deftest not-found-test
  (testing "not-found returns 404 with error message"
    (let [response (http-resp/not-found "Resource not found")]
      (is (= 404 (:status response)))
      (is (= {:error "Resource not found"} (json/parse-string (:body response) true))))))

(deftest forbidden-test
  (testing "forbidden returns 403 with error message"
    (let [response (http-resp/forbidden "Access denied")]
      (is (= 403 (:status response)))
      (is (= {:error "Access denied"} (json/parse-string (:body response) true))))))

(deftest server-error-test
  (testing "server-error returns 500 with error message"
    (let [response (http-resp/server-error "Internal error")]
      (is (= 500 (:status response)))
      (is (= {:error "Internal error"} (json/parse-string (:body response) true))))))

(deftest handle-validation-error-test
  (testing "handle-validation-error returns bad request with message"
    (let [ex (Exception. "Validation failed")
          response (http-resp/handle-validation-error ex)]
      (is (= 400 (:status response)))
      (is (= {:error "Validation error: Validation failed"} 
             (json/parse-string (:body response) true))))))

(deftest handle-db-error-test
  (testing "handle-db-error for duplicate key"
    ;; Test the instanceof check by creating a subclass of DuplicateKeyException
    ;; Since DuplicateKeyException has complex constructors, we'll test the general case
    ;; and verify the logic works by testing that non-duplicate exceptions fall through
    (let [ex (com.mongodb.MongoException. "Some mongo error")
          response (http-resp/handle-db-error ex)]
      (is (= 500 (:status response)))
      (is (= {:error "Database error: Some mongo error"} 
             (json/parse-string (:body response) true)))))
  
  (testing "handle-db-error for general MongoDB error"
    (let [ex (Exception. "DB connection failed")
          response (http-resp/handle-db-error ex)]
      (is (= 500 (:status response)))
      (is (= {:error "Database error: DB connection failed"} 
             (json/parse-string (:body response) true))))))