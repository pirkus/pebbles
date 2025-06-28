(ns pebbles.handlers-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [cheshire.core :as json]
   [pebbles.handlers :as handlers]
   [pebbles.test-utils :as test-utils]))

(def test-db (atom nil))
(def test-client-krn "krn:clnt:test-client")

(defn db-fixture [f]
  (let [db-map (test-utils/fresh-db)]
    (reset! test-db (:db db-map))
    (try
      (f)
      (finally
        (test-utils/cleanup-db db-map)))))

(use-fixtures :each db-fixture)

(deftest validate-progress-update-request-test
  (testing "returns nil when request is valid"
    (is (nil? (handlers/validate-progress-update-request
               "test@example.com"
               test-client-krn
               nil
               nil))))
  
  (testing "returns forbidden when email is nil"
    (let [response (handlers/validate-progress-update-request
                   nil
                   test-client-krn
                   nil
                   nil)
          body (json/parse-string (:body response) true)]
      (is (= 403 (:status response)))
      (is (= "No email found in authentication token" (:error body)))))
  
  (testing "returns bad-request when client-krn is nil"
    (let [response (handlers/validate-progress-update-request
                   "test@example.com"
                   nil
                   nil
                   nil)
          body (json/parse-string (:body response) true)]
      (is (= 400 (:status response)))
      (is (= "clientKrn path parameter is required" (:error body)))))
  
  (testing "returns forbidden when file exists but user is not creator"
    (let [any-existing {:email "other@example.com"}
          response (handlers/validate-progress-update-request
                   "test@example.com"
                   test-client-krn
                   any-existing
                   nil)
          body (json/parse-string (:body response) true)]
      (is (= 403 (:status response)))
      (is (= "Only the original creator can update this file's progress" 
             (:error body)))))
  
  (testing "returns bad-request when progress is already completed"
    (let [existing {:email "test@example.com" :isCompleted true}
          response (handlers/validate-progress-update-request
                   "test@example.com"
                   test-client-krn
                   existing
                   existing)
          body (json/parse-string (:body response) true)]
      (is (= 400 (:status response)))
      (is (= "This file processing has already been completed" 
             (:error body))))))

(deftest format-progress-response-test
  (testing "formats progress document correctly"
    (let [progress {:_id "507f1f77bcf86cd799439011"
                   :clientKrn test-client-krn
                   :filename "test.csv"
                   :email "test@example.com"
                   :counts {:done 10 :warn 2 :failed 1}}
          formatted (handlers/format-progress-response progress)]
      (is (= "507f1f77bcf86cd799439011" (:id formatted)))
      (is (nil? (:_id formatted)))
      (is (= test-client-krn (:clientKrn formatted)))
      (is (= "test.csv" (:filename formatted))))))

(deftest format-progress-list-test
  (testing "formats and sorts progress list by most recent first"
    (let [progress-list [{:_id "1" :updatedAt "2024-01-01T10:00:00Z"}
                        {:_id "2" :updatedAt "2024-01-01T12:00:00Z"}
                        {:_id "3" :updatedAt "2024-01-01T11:00:00Z"}]
          formatted (handlers/format-progress-list progress-list)]
      (is (= 3 (count formatted)))
      (is (= "2" (:id (first formatted))))  ; Most recent
      (is (= "3" (:id (second formatted)))) ; Middle
      (is (= "1" (:id (nth formatted 2)))) ; Oldest
      (is (every? #(nil? (:_id %)) formatted)))))

(deftest health-handler-test
  (testing "Health endpoint returns OK"
    (let [handler (handlers/health-handler)
          response (handler {})]
      (is (= 200 (:status response)))
      (is (= "OK" (:body response)))))) 