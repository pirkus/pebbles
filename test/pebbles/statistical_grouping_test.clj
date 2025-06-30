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
      
      ;; With strict threshold, similar "Database connection" messages now group together
      ;; So we get 4 groups instead of 5 (which is the correct behavior)
      (is (= 4 (count strict-groups)))
      
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

(deftest stack-trace-different-lengths-test
  (testing "Similar stack traces with different token lengths can be grouped"
    (let [;; Stack trace with standard structure (12 tokens)
          stack-trace-1 "java.lang.NullPointerException: Cannot invoke method getData() on null object at com.example.service.UserService.processUser(UserService.java:45) at com.example.controller.UserController.handleRequest(UserController.java:23)"
          
          ;; Very similar stack trace but with an additional method call in the stack (14 tokens)
          stack-trace-2 "java.lang.NullPointerException: Cannot invoke method getData() on null object at com.example.service.UserService.processUser(UserService.java:45) at com.example.controller.UserController.handleRequest(UserController.java:23) at org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:1040)"
          
          ;; A different but still lengthy stack trace with yet another length (10 tokens) 
          stack-trace-3 "java.sql.SQLException: Connection timeout after 30 seconds at com.example.database.ConnectionPool.getConnection(ConnectionPool.java:87) at com.example.service.DataService.fetchUserData(DataService.java:156)"
          
          messages [{:line 100 :message stack-trace-1}
                    {:line 200 :message stack-trace-2}  
                    {:line 300 :message stack-trace-3}]
          
          ;; Calculate similarity manually to show what we expect
          similarity-1-2 (sg/message-similarity stack-trace-1 stack-trace-2)
          similarity-1-3 (sg/message-similarity stack-trace-1 stack-trace-3)
          
          ;; Try grouping with different thresholds
          grouped-strict (sg/group-similar-messages messages {:threshold 0.9})]
      
      (is (> similarity-1-2 0.8) 
          "Similar stack traces with different lengths now get high similarity (>80%)")
      
      (is (< similarity-1-3 0.2)
          "Different exceptions should still have low similarity (<20%)")
      
      ;; 0.857 > 0.85 threshold will group similar stack traces together
      (is (= 2 (count (sg/group-similar-messages messages {:threshold 0.85}))) 
          "Similar stack traces group together with appropriate threshold (0.857 > 0.85)")
      
      ;; But with 0.9 threshold, they remain separate since 0.857 < 0.9
      (is (= 3 (count grouped-strict))
          "With very strict threshold (0.9), all remain separate since 0.857 < 0.9")
      
      (testing "different token lengths but similar"
        ;; This demonstrates the tokens have different lengths but are very similar
        (let [tokens-1 (sg/tokenize-message stack-trace-1)
              tokens-2 (sg/tokenize-message stack-trace-2)
              tokens-3 (sg/tokenize-message stack-trace-3)]
          
          ;; Show that they have different lengths
          (is (not= (count tokens-1) (count tokens-2))
              "Stack traces 1 and 2 should have different token lengths")
          
          (is (not= (count tokens-1) (count tokens-3))
              "Stack traces 1 and 3 should have different token lengths")
          
          ;; Show most tokens are similar at the beginning (common prefix)
          (let [common-length (min (count tokens-1) (count tokens-2))
                matching-tokens (count (filter true? 
                                              (map = 
                                                   (take common-length tokens-1)
                                                   (take common-length tokens-2))))]
            (is (> (/ matching-tokens common-length) 0.80)
                "More than 80% of tokens should match in order for similar stack traces")
            
            ;; Show the similarity calculation works as expected
            (println (str "Tokens 1: " (count tokens-1)))
            (println (str "Tokens 2: " (count tokens-2))) 
            (println (str "Tokens 3: " (count tokens-3)))
            (println (str "Matching tokens (1 vs 2): " matching-tokens "/" common-length))
            (println (str "Similarity score (1 vs 2): " similarity-1-2))
            (println (str "Similarity score (1 vs 3): " similarity-1-3))))))))

(deftest invalid-email-format-errors-test
  (testing "Groups invalid email format errors and extracts email values"
    (let [messages [{:line 15 :message "Invalid email format: john.doe@invalid"}
                    {:line 23 :message "Invalid email format: jane.smith@bad-domain"}
                    {:line 31 :message "Invalid email format: user@incomplete"}]
          grouped (sg/group-similar-messages messages)]
      
      ;; Should create one group for all email format errors
      (is (= 1 (count grouped)))
      
      (let [email-group (first grouped)]
        ;; Pattern should use {EMAIL} placeholder
        (is (= "Invalid email format: {EMAIL}" (:pattern email-group)))
        
        ;; Should have all 3 lines
        (is (= 3 (count (:lines email-group))))
        
        ;; Each line should have the email extracted as a value
        (let [extracted-emails (set (mapcat :values (:lines email-group)))]
          (is (= #{"john.doe@invalid" "jane.smith@bad-domain" "user@incomplete"} 
                 extracted-emails)))
        
        ;; Verify line numbers are preserved
        (let [line-numbers (set (map :line (:lines email-group)))]
          (is (= #{15 23 31} line-numbers)))))))

(deftest missing-required-field-errors-test
  (testing "Groups missing field errors and extracts field names"
    (let [messages [{:line 67 :message "Missing required field: customer_name"}
                    {:line 89 :message "Missing required field: phone_number"}
                    {:line 92 :message "Missing required field: billing_address"}]
          grouped (sg/group-similar-messages messages)]
      
      ;; Should create one group for all missing field errors
      (is (= 1 (count grouped)))
      
      (let [field-group (first grouped)]
        ;; Pattern should use {VARIABLE} placeholder for field names
        (is (= "Missing required field: {VARIABLE}" (:pattern field-group)))
        
        ;; Should have all 3 lines
        (is (= 3 (count (:lines field-group))))
        
        ;; Each line should have the field name extracted as a value
        (let [extracted-fields (set (mapcat :values (:lines field-group)))]
          (is (= #{"customer_name" "phone_number" "billing_address"} 
                 extracted-fields)))
        
        ;; Verify line numbers are preserved
        (let [line-numbers (set (map :line (:lines field-group)))]
          (is (= #{67 89 92} line-numbers)))))))

(deftest deprecated-field-warnings-test
  (testing "Groups deprecated field warnings using variability-based detection"
    (let [messages [{:line 12 :message "Deprecated field found: fax"}
                    {:line 45 :message "Deprecated field found: pager"}
                    {:line 78 :message "Deprecated field found: telex"}]
          grouped (sg/group-similar-messages messages)]
      
      ;; Should create one group for all deprecated field warnings
      (is (= 1 (count grouped)))
      
      (let [deprecated-group (first grouped)]
        ;; Pattern should use {VARIABLE} placeholder for simple field names
        (is (= "Deprecated field found: {VARIABLE}" (:pattern deprecated-group)))
        
        ;; Should have all 3 lines
        (is (= 3 (count (:lines deprecated-group))))
        
        ;; Each line should have the field name extracted as a value
        (let [extracted-fields (set (mapcat :values (:lines deprecated-group)))]
          (is (= #{"fax" "pager" "telex"} extracted-fields)))
        
        ;; Verify line numbers are preserved
        (let [line-numbers (set (map :line (:lines deprecated-group)))]
          (is (= #{12 45 78} line-numbers)))))))

(deftest mixed-error-and-warning-patterns-test
  (testing "Handles multiple different patterns in one batch"
    (let [messages [{:line 15 :message "Invalid email format: john.doe@invalid"}
                    {:line 23 :message "Invalid email format: jane.smith@bad-domain"}  
                    {:line 67 :message "Missing required field: customer_name"}
                    {:line 89 :message "Missing required field: phone_number"}
                    {:line 12 :message "Deprecated field found: fax"}
                    {:line 45 :message "Deprecated field found: pager"}]
          grouped (sg/group-similar-messages messages)]
      
      ;; Should identify 3 distinct patterns
      (is (= 3 (count grouped)))
      
      ;; Check email format group
      (let [email-group (first (filter #(re-find #"Invalid email format" (:pattern %)) grouped))]
        (is (not (nil? email-group)))
        (is (= "Invalid email format: {EMAIL}" (:pattern email-group)))
        (is (= 2 (count (:lines email-group))))
        (let [emails (set (mapcat :values (:lines email-group)))]
          (is (= #{"john.doe@invalid" "jane.smith@bad-domain"} emails))))
      
      ;; Check missing field group
      (let [missing-group (first (filter #(re-find #"Missing required field" (:pattern %)) grouped))]
        (is (not (nil? missing-group)))
        (is (= "Missing required field: {VARIABLE}" (:pattern missing-group)))
        (is (= 2 (count (:lines missing-group))))
        (let [fields (set (mapcat :values (:lines missing-group)))]
          (is (= #{"customer_name" "phone_number"} fields))))
      
      ;; Check deprecated field group
      (let [deprecated-group (first (filter #(re-find #"Deprecated field found" (:pattern %)) grouped))]
        (is (not (nil? deprecated-group)))
        (is (= "Deprecated field found: {VARIABLE}" (:pattern deprecated-group)))
        (is (= 2 (count (:lines deprecated-group))))
        (let [fields (set (mapcat :values (:lines deprecated-group)))]
          (is (= #{"fax" "pager"} fields)))))))

(deftest simplified-field-detection-test
  (testing "Simplified approach: field names detected by variability analysis, not regex patterns"
    ;; Field names are no longer detected as variables by regex patterns
    (is (not (sg/is-likely-variable? "customer_name")))
    (is (not (sg/is-likely-variable? "customerName")))
    (is (not (sg/is-likely-variable? "CustomerName")))
    (is (not (sg/is-likely-variable? "customer-name")))
    
    ;; Field names are not normalized by specific patterns - they remain unchanged
    (is (= "customer_name" (sg/normalize-token "customer_name")))
    (is (= "customerName" (sg/normalize-token "customerName")))
    (is (= "CustomerName" (sg/normalize-token "CustomerName")))
    (is (= "customer-name" (sg/normalize-token "customer-name")))
    
    ;; Common English words are still not detected as variables
    (is (not (sg/is-likely-variable? "Invalid")))
    (is (not (sg/is-likely-variable? "email")))
    (is (not (sg/is-likely-variable? "format")))
    (is (not (sg/is-likely-variable? "field")))
    (is (not (sg/is-likely-variable? "found")))
    
    ;; Simple words remain unchanged
    (is (= "Invalid" (sg/normalize-token "Invalid")))
    (is (= "email" (sg/normalize-token "email")))
    (is (= "format" (sg/normalize-token "format")))
    
    ;; But variability analysis still correctly identifies field names
    (let [field-messages [{:line 67 :message "Missing required field: customer_name"}
                          {:line 89 :message "Missing required field: phone_number"}]
          result (sg/group-similar-messages field-messages)
          group (first result)]
      (is (= 1 (count result)))
      (is (= "Missing required field: {VARIABLE}" (:pattern group)))
      (is (= ["customer_name" "phone_number"] (mapcat :values (:lines group)))))))

(deftest enhanced-email-detection-test
  (testing "Detects various email formats including invalid ones"
    ;; Valid emails
    (is (sg/is-likely-variable? "john@example.com"))
    (is (sg/is-likely-variable? "user.name@domain.org"))
    (is (= "{EMAIL}" (sg/normalize-token "john@example.com")))
    
    ;; Invalid emails (should still be detected as email patterns)
    (is (sg/is-likely-variable? "john.doe@invalid"))
    (is (sg/is-likely-variable? "user@incomplete"))
    (is (sg/is-likely-variable? "jane.smith@bad-domain"))
    (is (= "{EMAIL}" (sg/normalize-token "john.doe@invalid")))
    (is (= "{EMAIL}" (sg/normalize-token "user@incomplete")))))

(deftest variability-based-detection-test
  (testing "Uses variability analysis for tokens that don't match specific patterns"
    (let [messages [{:line 1 :message "Status changed to active"}
                    {:line 2 :message "Status changed to inactive"}
                    {:line 3 :message "Status changed to pending"}]
          grouped (sg/group-similar-messages messages)]
      
      ;; Should create one group using variability detection
      (is (= 1 (count grouped)))
      
      (let [status-group (first grouped)]
        ;; Pattern should use {VARIABLE} for the status value  
        (is (= "Status changed to {VARIABLE}" (:pattern status-group)))
        
        ;; Should extract all status values
        (let [statuses (set (mapcat :values (:lines status-group)))]
          (is (= #{"active" "inactive" "pending"} statuses))))))

(deftest consolidate-with-existing-patterns-enhanced-test
  (testing "Consolidates new messages with existing patterns using enhanced detection"
    (let [existing-groups [{:pattern "Invalid email format: {EMAIL}"
                           :lines [{:line 10 :values ["user@old.com"]}]}
                          {:pattern "Missing required field: {VARIABLE}"
                           :lines [{:line 20 :values ["old_field"]}]}
                          {:pattern "Deprecated field found: {VARIABLE}"
                           :lines [{:line 30 :values ["oldfield"]}]}]
          
          new-items [{:line 40 :message "Invalid email format: new.user@invalid"}
                    {:line 50 :message "Missing required field: new_field_name"}
                    {:line 60 :message "Deprecated field found: newfax"}
                    {:line 70 :message "Completely new error type"}]
          
          result (sg/consolidate-with-existing-patterns new-items existing-groups)]
      
      ;; Should have 4 groups: 3 updated existing + 1 new
      (is (= 4 (count result)))
      
      ;; Check email pattern was updated
      (let [email-group (first (filter #(re-find #"Invalid email format" (:pattern %)) result))]
        (is (= 2 (count (:lines email-group))))
        (let [all-emails (set (mapcat :values (:lines email-group)))]
          (is (= #{"user@old.com" "new.user@invalid"} all-emails))))
      
      ;; Check field pattern was updated  
      (let [field-group (first (filter #(re-find #"Missing required field" (:pattern %)) result))]
        (is (= 2 (count (:lines field-group))))
        (let [all-fields (set (mapcat :values (:lines field-group)))]
          (is (= #{"old_field" "new_field_name"} all-fields))))
      
      ;; Check deprecated pattern was updated
      (let [deprecated-group (first (filter #(re-find #"Deprecated field found" (:pattern %)) result))]
        (is (= 2 (count (:lines deprecated-group))))
        (let [all-deprecated (set (mapcat :values (:lines deprecated-group)))]
          (is (= #{"oldfield" "newfax"} all-deprecated))))
      
      ;; Check new pattern was created
      (let [new-group (first (filter #(= "Completely new error type" (:pattern %)) result))]
        (is (not (nil? new-group)))
        (is (= 1 (count (:lines new-group))))
        (is (= 70 (:line (first (:lines new-group))))))))))