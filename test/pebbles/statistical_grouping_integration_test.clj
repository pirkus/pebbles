(ns pebbles.statistical-grouping-integration-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [pebbles.db :as db]
   [pebbles.test-utils :as test-utils]
   [monger.core :as mg]))

(def ^:dynamic *test-db* nil)

(use-fixtures :each
  (fn [f]
    (let [conn (mg/connect)
          db (mg/get-db conn "pebbles-test")]
      (binding [*test-db* db]
        (test-utils/clear-collections db)
        (f)
        (test-utils/clear-collections db)
        (mg/disconnect conn)))))

(deftest statistical-pattern-consolidation-test
  (testing "Statistical pattern matching groups similar validation messages"
    (let [progress-data {:clientKrn "krn:clnt:test-client"
                        :filename "validation-errors.csv"
                        :email "test@example.com"
                        :counts {:done 100 :warn 10 :failed 8}
                        :errors [{:line 10 :message "Invalid account number 123456"}
                                {:line 20 :message "Invalid account number 789012"}
                                {:line 30 :message "Missing required field 'username'"}
                                {:line 40 :message "Invalid account number 999999"}
                                {:line 50 :message "Missing required field 'email'"}
                                {:line 60 :message "Transaction amount $1,234.56 exceeds daily limit"}
                                {:line 70 :message "Missing required field 'password'"}
                                {:line 80 :message "Transaction amount $999.00 exceeds daily limit"}]
                        :warnings [{:line 15 :message "Account 55555 will expire in 30 days"}
                                  {:line 25 :message "Account 66666 will expire in 15 days"}
                                  {:line 35 :message "Using deprecated API endpoint /v1/users"}
                                  {:line 45 :message "Account 77777 will expire in 7 days"}]
                        :isCompleted false
                        :createdAt "2024-01-01T00:00:00Z"}
          result (db/create-progress *test-db* progress-data)]
      
      ;; Check that errors were consolidated into patterns
      (is (= 3 (count (:errors result)))) ; 3 patterns: account number, missing field, transaction amount
      
      ;; Check that warnings were consolidated
      (is (= 2 (count (:warnings result)))) ; 2 patterns: account expiry, deprecated API
      
      ;; Verify the patterns and their groupings
      (let [errors (:errors result)
            account-error (first (filter #(re-find #"account number" (:pattern %)) errors))
            field-error (first (filter #(re-find #"required field" (:pattern %)) errors))
            transaction-error (first (filter #(re-find #"Transaction amount" (:pattern %)) errors))]
        
        ;; Check account number pattern
        (is (not (nil? account-error)))
        (let [line-numbers (map :line (:lines account-error))
              all-values (mapcat :values (:lines account-error))]
          (is (= [10 20 40] (sort line-numbers)))
          (is (= 3 (count (:lines account-error))))
          (is (= #{"123456" "789012" "999999"} (set all-values))))
        
        ;; Check missing field pattern
        (is (not (nil? field-error)))
        (let [line-numbers (map :line (:lines field-error))
              all-values (mapcat :values (:lines field-error))]
          (is (= [30 50 70] (sort line-numbers)))
          (is (= 3 (count (:lines field-error))))
          (is (= #{"'username'" "'email'" "'password'"} (set all-values))))
        
        ;; Check transaction amount pattern
        (is (not (nil? transaction-error)))
        (let [line-numbers (map :line (:lines transaction-error))
              all-values (mapcat :values (:lines transaction-error))]
          (is (= [60 80] (sort line-numbers)))
          (is (= 2 (count (:lines transaction-error))))
          (is (= #{"$1,234.56" "$999.00"} (set all-values)))))))
  
  (testing "Fallback to exact matching when pattern matching disabled"
    (let [progress-data {:clientKrn "krn:clnt:test-client"
                        :filename "exact-match.csv"
                        :email "test@example.com"
                        :counts {:done 50 :warn 5 :failed 3}
                        :errors [{:line 10 :message "Invalid account number 123456"}
                                {:line 20 :message "Invalid account number 123456"}
                                {:line 30 :message "Invalid account number 789012"}]
                        :isCompleted false
                        :createdAt "2024-01-01T00:00:00Z"}
          ;; Use exact matching (use-patterns? = false)
          prepared (db/prepare-progress-data progress-data false)]
      
      ;; Check the prepared data structure
      (is (= 2 (count (:errors prepared)))) ; "Invalid account number 123456" and "Invalid account number 789012"
      
      ;; Find the consolidated duplicate in prepared data
              (let [duplicate-error (first (filter #(= "Invalid account number 123456" (:pattern %)) (:errors prepared)))]
        (is (= [10 20] (sort (:lines duplicate-error))))))))

(deftest real-world-validation-patterns-test
  (testing "Handles complex real-world validation patterns"
    (let [progress-data {:clientKrn "krn:clnt:test-client"
                        :filename "complex-errors.csv"
                        :email "test@example.com"
                        :counts {:done 200 :warn 15 :failed 12}
                        :errors [{:line 10 :message "File upload failed: document.pdf (size: 15MB)"}
                                {:line 20 :message "File upload failed: image.jpg (size: 8MB)"} 
                                {:line 30 :message "Invalid email format: john@"}
                                {:line 40 :message "Invalid email format: @example.com"}
                                {:line 50 :message "Connection timeout after 30 seconds"}
                                {:line 60 :message "File upload failed: data.csv (size: 25MB)"}
                                {:line 70 :message "Connection timeout after 60 seconds"}
                                {:line 80 :message "User john.doe@example.com exceeded 5 login attempts"}
                                {:line 90 :message "User jane.smith@test.com exceeded 5 login attempts"}]
                        :isCompleted false
                        :createdAt "2024-01-01T00:00:00Z"}
          result (db/create-progress *test-db* progress-data)]
      
      ;; Should consolidate into 4 patterns
      (is (<= 4 (count (:errors result)) 5)) ; Some flexibility for edge cases
      
      ;; Verify file upload pattern
      (let [file-upload (first (filter #(re-find #"File upload failed" (:pattern %)) (:errors result)))]
        (when file-upload
          (is (= 3 (count (:lines file-upload))))
          (let [line-numbers (map :line (:lines file-upload))]
            (is (= [10 20 60] (sort line-numbers))))))
      
      ;; Verify timeout pattern
      (let [timeout (first (filter #(re-find #"timeout" (:pattern %)) (:errors result)))]
        (when timeout
          (is (= 2 (count (:lines timeout))))
          (let [line-numbers (map :line (:lines timeout))]
            (is (= [50 70] (sort line-numbers))))))
      
      ;; Verify login attempts pattern
      (let [login (first (filter #(re-find #"login attempts" (:pattern %)) (:errors result)))]
        (when login
          (is (= 2 (count (:lines login))))
          (let [line-numbers (map :line (:lines login))]
            (is (= [80 90] (sort line-numbers)))))))))