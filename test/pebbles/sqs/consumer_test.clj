(ns pebbles.sqs.consumer-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [cheshire.core :as json]
   [pebbles.sqs.consumer :as sqs-consumer]
   [pebbles.test-utils :as test-utils]
   [pebbles.db :as db]))

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

;; ----------------------------------------------------------------------------
;; Message Parsing Tests
;; ----------------------------------------------------------------------------

(deftest parse-sqs-message-test
  (testing "Valid message parsing"
    (let [message {:Body (json/generate-string
                         {:filename "test.csv"
                          :counts {:done 10 :warn 2 :failed 1}
                          :clientKrn test-client-krn
                          :email "test@example.com"})}
          result (sqs-consumer/parse-sqs-message message)]
      (is (= "test.csv" (:filename result)))
      (is (= {:done 10 :warn 2 :failed 1} (:counts result)))
      (is (= test-client-krn (:clientKrn result)))
      (is (= "test@example.com" (:email result)))))
  
  (testing "Missing required fields"
    (doseq [missing-field [:filename :counts :clientKrn :email]]
      (let [base-data {:filename "test.csv"
                       :counts {:done 10 :warn 2 :failed 1}
                       :clientKrn test-client-krn
                       :email "test@example.com"}
            incomplete-data (dissoc base-data missing-field)
            message {:Body (json/generate-string incomplete-data)}]
        (is (thrown? Exception (sqs-consumer/parse-sqs-message message))
            (str "Should throw exception when missing " missing-field)))))
  
  (testing "Invalid JSON"
    (let [message {:Body "invalid json {}"}]
      (is (thrown? Exception (sqs-consumer/parse-sqs-message message))))))

;; ----------------------------------------------------------------------------
;; Message Processing Tests  
;; ----------------------------------------------------------------------------

(deftest process-progress-message-test
  (testing "Process new progress message"
    (let [message {:Body (json/generate-string
                         {:filename "new-file.csv"
                          :counts {:done 20 :warn 1 :failed 0}
                          :total 200
                          :clientKrn test-client-krn
                          :email "test@example.com"})}
          result (sqs-consumer/process-progress-message @test-db message)]
      (is (= :success (:status result)))
      (is (= "created" (get-in result [:result :result])))
      (is (= "new-file.csv" (get-in result [:result :filename])))
      (is (= {:done 20 :warn 1 :failed 0} (get-in result [:result :counts])))
      
      ;; Verify in database
      (let [saved (db/find-progress @test-db test-client-krn "new-file.csv" "test@example.com")]
        (is (= test-client-krn (:clientKrn saved)))
        (is (= "new-file.csv" (:filename saved)))
        (is (= "test@example.com" (:email saved)))
        (is (= {:done 20 :warn 1 :failed 0} (:counts saved)))
        (is (= 200 (:total saved))))))
  
  (testing "Process update to existing progress"
    (let [;; First create progress
          create-message {:Body (json/generate-string
                               {:filename "update-file.csv"
                                :counts {:done 10 :warn 0 :failed 0}
                                :clientKrn test-client-krn
                                :email "test@example.com"})}
          _ (sqs-consumer/process-progress-message @test-db create-message)
          
          ;; Then update it
          update-message {:Body (json/generate-string
                               {:filename "update-file.csv"
                                :counts {:done 15 :warn 2 :failed 1}
                                :clientKrn test-client-krn
                                :email "test@example.com"})}
          result (sqs-consumer/process-progress-message @test-db update-message)]
      
      (is (= :success (:status result)))
      (is (= "updated" (get-in result [:result :result])))
      ;; Counts should be added, not replaced
      (is (= {:done 25 :warn 2 :failed 1} (get-in result [:result :counts])))
      
      ;; Verify in database
      (let [saved (db/find-progress @test-db test-client-krn "update-file.csv" "test@example.com")]
        (is (= {:done 25 :warn 2 :failed 1} (:counts saved))))))
  
  (testing "Process message with completion flag"
    (let [message {:Body (json/generate-string
                         {:filename "complete-file.csv"
                          :counts {:done 100 :warn 0 :failed 0}
                          :total 100
                          :isLast true
                          :clientKrn test-client-krn
                          :email "test@example.com"})}
          result (sqs-consumer/process-progress-message @test-db message)]
      (is (= :success (:status result)))
      (is (true? (get-in result [:result :isCompleted])))
      
      ;; Verify in database
      (let [saved (db/find-progress @test-db test-client-krn "complete-file.csv" "test@example.com")]
        (is (true? (:isCompleted saved))))))
  
  (testing "Validation error - wrong user trying to update"
    (let [;; Create progress with one user
          create-message {:Body (json/generate-string
                               {:filename "restricted-file.csv"
                                :counts {:done 10 :warn 0 :failed 0}
                                :clientKrn test-client-krn
                                :email "creator@example.com"})}
          _ (sqs-consumer/process-progress-message @test-db create-message)
          
          ;; Try to update with different user
          update-message {:Body (json/generate-string
                               {:filename "restricted-file.csv"
                                :counts {:done 5 :warn 0 :failed 0}
                                :clientKrn test-client-krn
                                :email "other@example.com"})}
          result (sqs-consumer/process-progress-message @test-db update-message)]
      
      (is (= :validation-error (:status result)))
      (is (re-find #"Only the original creator can update" (:error result)))))
  
  (testing "Process message with errors and warnings"
    (let [message {:Body (json/generate-string
                         {:filename "errors-warnings.csv"
                          :counts {:done 80 :warn 2 :failed 3}
                          :clientKrn test-client-krn
                          :email "test@example.com"
                          :errors [{:line 10 :message "Invalid date format"}
                                   {:line 25 :message "Missing required field"}
                                   {:line 30 :message "Duplicate entry"}]
                          :warnings [{:line 15 :message "Deprecated field used"}
                                     {:line 40 :message "Value exceeds recommended range"}]})}
          result (sqs-consumer/process-progress-message @test-db message)]
      
      (is (= :success (:status result)))
      (is (= 3 (count (get-in result [:result :errors]))))
      (is (= 2 (count (get-in result [:result :warnings]))))
      
      ;; Verify in database
      (let [saved (db/find-progress @test-db test-client-krn "errors-warnings.csv" "test@example.com")]
        (is (= 3 (count (:errors saved))))
        (is (= 2 (count (:warnings saved)))))))) ; Close deftest process-progress-message-test



;; ----------------------------------------------------------------------------
;; Error Handling Tests
;; ----------------------------------------------------------------------------

(deftest sqs-error-handling-test
  (testing "Invalid message format handling"
    (doseq [invalid-msg [{:Body ""}
                         {:Body "invalid json"}
                         {:Body "{}"}
                         {:Body (json/generate-string {:filename "test.csv"})}
                         {:Body (json/generate-string {:counts {:done 10}})}]]
      (let [result (sqs-consumer/process-progress-message @test-db invalid-msg)]
        (is (= :error (:status result)))
        (is (some? (:error result)))))))
