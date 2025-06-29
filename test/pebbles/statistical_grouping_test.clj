(ns pebbles.statistical-grouping-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [pebbles.statistical-grouping :as sg]))

(deftest tokenize-message-test
  (testing "Tokenizes messages into words preserving quoted strings"
    (is (= ["Invalid" "account" "number" "123456"]
           (sg/tokenize-message "Invalid account number 123456")))
    (is (= ["Field" "'username'" "is" "required"]
           (sg/tokenize-message "Field 'username' is required")))
    (is (= ["Error" "in" "file" "/path/to/file.txt" "at" "line" "42"]
           (sg/tokenize-message "Error in file /path/to/file.txt at line 42")))))

(deftest token-variability-test
  (testing "Calculates token variability to identify which tokens are data vs template"
    (let [messages ["Invalid account number 123456"
                    "Invalid account number 789012"
                    "Invalid account number 555555"]
          tokenized (map sg/tokenize-message messages)
          variability (sg/calculate-token-variability tokenized)]
      ;; First 3 tokens should have low variability (template text)
      (is (= 0.0 (nth variability 0)))  ; "Invalid"
      (is (= 0.0 (nth variability 1)))  ; "account" 
      (is (= 0.0 (nth variability 2)))  ; "number"
      ;; Last token should have high variability (data)
      (is (= 1.0 (nth variability 3))))))  ; Different numbers

(deftest normalize-message-test
  (testing "Normalizes messages by replacing variable tokens with placeholders"
    (let [messages ["Invalid account number 123456"
                    "Invalid account number 789012"
                    "Invalid account number 555555"]
          normalized (sg/normalize-messages messages)]
      ;; All should normalize to the same pattern
      (is (= 1 (count (distinct (map :pattern normalized)))))
      (is (= "Invalid account number {NUMBER}" (:pattern (first normalized)))))))

(deftest group-similar-messages-test
  (testing "Groups messages by their statistical similarity"
    (let [messages [{:line 10 :message "Invalid account number 123456"}
                    {:line 20 :message "Invalid account number 789012"}
                    {:line 30 :message "Missing required field 'username'"}
                    {:line 40 :message "Missing required field 'email'"}
                    {:line 50 :message "Connection timeout after 30 seconds"}
                    {:line 60 :message "Invalid account number 999999"}
                    {:line 70 :message "Missing required field 'password'"}
                    {:line 80 :message "Connection timeout after 60 seconds"}]
          grouped (sg/group-similar-messages messages)]
      
      ;; Should identify 3 distinct patterns
      (is (= 3 (count grouped)))
      
      ;; Check account number group
      (let [account-group (first (filter #(re-find #"account number" (:pattern %)) grouped))
            line-numbers (map :line (:lines account-group))
            all-values (mapcat :values (:lines account-group))]
        (is (= [10 20 60] line-numbers))
        (is (= #{"123456" "789012" "999999"} (set all-values))))
      
      ;; Check missing field group
      (let [field-group (first (filter #(re-find #"required field" (:pattern %)) grouped))
            line-numbers (map :line (:lines field-group))
            all-values (mapcat :values (:lines field-group))]
        (is (= [30 40 70] line-numbers))
        (is (= #{"'username'" "'email'" "'password'"} (set all-values))))
      
      ;; Check timeout group
      (let [timeout-group (first (filter #(re-find #"timeout" (:pattern %)) grouped))
            line-numbers (map :line (:lines timeout-group))
            all-values (mapcat :values (:lines timeout-group))]
        (is (= [50 80] line-numbers))
        (is (= #{"30" "60"} (set all-values)))))))

(deftest similarity-threshold-test
  (testing "Groups messages with configurable similarity threshold"
    (let [messages [{:line 1 :message "User john@example.com failed login"}
                    {:line 2 :message "User jane@test.com failed login"}
                    {:line 3 :message "User admin@site.org failed authentication"}
                    {:line 4 :message "Database connection lost"}
                    {:line 5 :message "Database connection timeout"}]
          ;; High threshold - only exact structural matches
          strict-groups (sg/group-similar-messages messages {:threshold 0.95})
          ;; Lower threshold - similar messages group together
          loose-groups (sg/group-similar-messages messages {:threshold 0.6})]
      
      ;; With strict threshold, each message forms its own group
      (is (= 5 (count strict-groups)))
      
      ;; With loose threshold, similar messages might group together
      (is (<= (count loose-groups) (count strict-groups))))))

(deftest real-world-validation-messages-test
  (testing "Handles real-world validation message patterns"
    (let [messages [{:line 1 :message "Invalid email format: john@"}
                    {:line 2 :message "Invalid email format: @example.com"}
                    {:line 3 :message "Account 12345 is locked"}
                    {:line 4 :message "Account 67890 is locked"}
                    {:line 5 :message "Transaction amount $1,234.56 exceeds limit"}
                    {:line 6 :message "Transaction amount $999.00 exceeds limit"}
                    {:line 7 :message "File upload failed: document.pdf (size: 15MB)"}
                    {:line 8 :message "File upload failed: image.jpg (size: 8MB)"}]
          grouped (sg/group-similar-messages messages)]
      
      ;; Should identify 4 patterns
      (is (= 4 (count grouped)))
      
      ;; Each pattern should have correct line groupings
      (is (every? #(>= (count (:lines %)) 2) grouped)))))

(deftest pattern-matching-test
  (testing "Pattern matches message correctly"
    (is (sg/pattern-matches-message? "Invalid account number {NUMBER}" 
                                    "Invalid account number 123456"))
    (is (sg/pattern-matches-message? "Missing field {QUOTED}" 
                                    "Missing field 'username'"))
    (is (not (sg/pattern-matches-message? "Invalid account number {NUMBER}" 
                                         "Invalid email address")))
    (is (not (sg/pattern-matches-message? "Error {NUMBER}" 
                                         "Error in file")))))

(deftest extract-values-for-pattern-test
  (testing "Extracts values from message based on pattern"
    (is (= ["123456"] 
           (sg/extract-values-for-pattern "Invalid account number {NUMBER}" 
                                         "Invalid account number 123456")))
    (is (= ["'username'"] 
           (sg/extract-values-for-pattern "Missing field {QUOTED}" 
                                         "Missing field 'username'")))
    (is (= ["john@example.com" "5"] 
           (sg/extract-values-for-pattern "User {EMAIL} has {NUMBER} failed attempts" 
                                         "User john@example.com has 5 failed attempts")))))

(deftest consolidate-with-existing-patterns-test
  (testing "Consolidates new messages using existing patterns"
    (let [existing-groups [{:pattern "Invalid account number {NUMBER}"
                           :lines [{:line 10 :values ["123"]}
                                  {:line 20 :values ["456"]}]
}
                          {:pattern "System error"
                           :lines [{:line 30}]
}]
          new-items [{:line 40 :message "Invalid account number 789"}
                    {:line 50 :message "Invalid account number 999"}
                    {:line 60 :message "New error type"}
                    {:line 70 :message "System error"}]
          result (sg/consolidate-with-existing-patterns new-items existing-groups)]
      
      ;; Should have 3 groups: updated account pattern, system error, and new error
      (is (= 3 (count result)))
      
      ;; Check updated account number pattern
      (let [account-group (first (filter #(re-find #"account number" (:pattern %)) result))]
        (is (= 4 (count (:lines account-group)))) ; 2 existing + 2 new
        (let [line-numbers (map :line (:lines account-group))]
          (is (= [10 20 40 50] (sort line-numbers))))
        (let [all-values (mapcat :values (:lines account-group))]
          (is (= #{"123" "456" "789" "999"} (set all-values)))))
      
      ;; Check system error group
      (let [system-group (first (filter #(= "System error" (:pattern %)) result))]
        (is (= 2 (count (:lines system-group)))) ; 1 existing + 1 new
        (let [line-numbers (map :line (:lines system-group))]
          (is (= [30 70] (sort line-numbers)))))
      
      ;; Check new error pattern
      (let [new-group (first (filter #(= "New error type" (:pattern %)) result))]
        (is (not (nil? new-group)))
        (when new-group
          (is (= 1 (count (:lines new-group))))
          (is (= 60 (:line (first (:lines new-group))))))))))

(deftest multiple-same-type-placeholders-test
  (testing "Correctly extracts multiple values when pattern has multiple placeholders of same type"
    (let [messages [{:line 10 :message "Account 12345 will expire in 30 days"}
                    {:line 20 :message "Account 67890 will expire in 15 days"}
                    {:line 30 :message "Account 99999 will expire in 7 days"}]
          grouped (sg/group-similar-messages messages)]
      
      ;; Should create one group with the account expiry pattern
      (is (= 1 (count grouped)))
      
      (let [expiry-group (first grouped)]
        ;; Verify the pattern has two {NUMBER} placeholders
        (is (= "Account {NUMBER} will expire in {NUMBER} days" (:pattern expiry-group)))
        
        ;; Check all 3 messages were grouped together
        (is (= 3 (count (:lines expiry-group))))
        
        ;; Verify each line extracts exactly 2 values (account number and days)
        (let [line10 (first (filter #(= 10 (:line %)) (:lines expiry-group)))
              line20 (first (filter #(= 20 (:line %)) (:lines expiry-group)))
              line30 (first (filter #(= 30 (:line %)) (:lines expiry-group)))]
          
          ;; Each should have exactly 2 values
          (is (= 2 (count (:values line10))))
          (is (= 2 (count (:values line20))))
          (is (= 2 (count (:values line30))))
          
          ;; Check the values are extracted in correct order
          (is (= ["12345" "30"] (:values line10)))
          (is (= ["67890" "15"] (:values line20)))
          (is (= ["99999" "7"] (:values line30)))))))
  
  (testing "Handles mixed placeholder types correctly"
    (let [messages [{:line 40 :message "Transfer $1,000.00 from account 111 to account 222"}
                    {:line 50 :message "Transfer $2,500.00 from account 333 to account 444"}]
          grouped (sg/group-similar-messages messages)]
      
      (is (>= 2 (count grouped))) ; May create 1 or 2 groups depending on similarity threshold
      
      (let [transfer-group (first (filter #(re-find #"Transfer" (:pattern %)) grouped))]
        (is (not (nil? transfer-group)))
        ;; Pattern should have one {AMOUNT} and two {NUMBER} placeholders
        (is (re-find #"Transfer.*from.*account.*to.*account" (:pattern transfer-group)))
        
        ;; Check values are extracted
        (let [lines (:lines transfer-group)]
          (is (>= (count lines) 1)) ; At least one transfer message
          (let [first-line (first lines)]
            ;; Should have at least 3 values (amount and two account numbers)
            (is (>= (count (:values first-line)) 3))))))))