(ns pebbles.progress-handler-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [pebbles.handlers :as handlers]
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

(deftest update-progress-handler-test
  (let [handler (handlers/update-progress-handler @test-db)
        email "test@example.com"
        identity {:email email}]
    
    (testing "Create new progress"
      (let [request (test-utils/make-test-request
                     {:filename "test-file.csv"
                      :counts {:done 10 :warn 2 :failed 1}
                      :total 100}
                     :identity identity
                     :path-params {:clientKrn test-client-krn})
            response (handler request)
            body (test-utils/parse-json-response response)]
        (is (= 200 (:status response)))
        (is (= "created" (:result body)))
        (is (= test-client-krn (:clientKrn body)))
        (is (= "test-file.csv" (:filename body)))
        (is (= {:done 10 :warn 2 :failed 1} (:counts body)))
        (is (= 100 (:total body)))
        (is (false? (:isCompleted body)))
        
        ;; Verify in database
        (let [saved (db/find-progress @test-db test-client-krn "test-file.csv" email)]
          (is (= test-client-krn (:clientKrn saved)))
          (is (= "test-file.csv" (:filename saved)))
          (is (= email (:email saved)))
          (is (= {:done 10 :warn 2 :failed 1} (:counts saved)))
          (is (= 100 (:total saved)))
          (is (false? (:isCompleted saved))))))
    
    (testing "Update existing progress"
      (let [;; First create a progress
            _ (handler (test-utils/make-test-request
                        {:filename "update-test.csv"
                         :counts {:done 5 :warn 0 :failed 0}}
                        :identity identity
                        :path-params {:clientKrn test-client-krn}))
            ;; Then update it
            update-request (test-utils/make-test-request
                            {:filename "update-test.csv"
                             :counts {:done 10 :warn 1 :failed 0}}
                            :identity identity
                            :path-params {:clientKrn test-client-krn})
            response (handler update-request)
            body (test-utils/parse-json-response response)]
        (is (= 200 (:status response)))
        (is (= "updated" (:result body)))
        (is (= {:done 15 :warn 1 :failed 0} (:counts body)))
        
        ;; Verify counts were added, not replaced
        (let [saved (db/find-progress @test-db test-client-krn "update-test.csv" email)]
          (is (= {:done 15 :warn 1 :failed 0} (:counts saved))))))
    
    (testing "Complete progress with isLast flag"
      (let [request (test-utils/make-test-request
                     {:filename "complete-test.csv"
                      :counts {:done 100 :warn 0 :failed 0}
                      :total 100
                      :isLast true}
                     :identity identity
                     :path-params {:clientKrn test-client-krn})
            response (handler request)
            body (test-utils/parse-json-response response)]
        (is (= 200 (:status response)))
        (is (true? (:isCompleted body)))
        
        ;; Verify in database
        (let [saved (db/find-progress @test-db test-client-krn "complete-test.csv" email)]
          (is (true? (:isCompleted saved))))))
    
    (testing "Reject update to completed progress"
      (let [;; First create and complete a progress
            _ (handler (test-utils/make-test-request
                        {:filename "locked-test.csv"
                         :counts {:done 100 :warn 0 :failed 0}
                         :isLast true}
                        :identity identity
                        :path-params {:clientKrn test-client-krn}))
            ;; Try to update it
            update-request (test-utils/make-test-request
                            {:filename "locked-test.csv"
                             :counts {:done 10 :warn 0 :failed 0}}
                            :identity identity
                            :path-params {:clientKrn test-client-krn})
            response (handler update-request)
            body (test-utils/parse-json-response response)]
        (is (= 400 (:status response)))
        (is (= "This file processing has already been completed" (:error body)))))
    
    (testing "Missing email in identity"
      (let [request (test-utils/make-test-request
                     {:filename "no-auth.csv"
                      :counts {:done 1 :warn 0 :failed 0}}
                     :identity {}
                     :path-params {:clientKrn test-client-krn})
            response (handler request)
            body (test-utils/parse-json-response response)]
        (is (= 403 (:status response)))
        (is (= "No email found in authentication token" (:error body)))))
    
    (testing "Missing clientKrn in path"
      (let [request (test-utils/make-test-request
                     {:filename "no-client.csv"
                      :counts {:done 1 :warn 0 :failed 0}}
                     :identity identity
                     :path-params {})
            response (handler request)
            body (test-utils/parse-json-response response)]
        (is (= 400 (:status response)))
        (is (= "clientKrn path parameter is required" (:error body)))))
    
    (testing "Add total to existing progress"
      (let [;; Create without total
            _ (handler (test-utils/make-test-request
                        {:filename "add-total.csv"
                         :counts {:done 5 :warn 0 :failed 0}}
                        :identity identity
                        :path-params {:clientKrn test-client-krn}))
            ;; Update with total
            update-request (test-utils/make-test-request
                            {:filename "add-total.csv"
                             :counts {:done 5 :warn 0 :failed 0}
                             :total 200}
                            :identity identity
                            :path-params {:clientKrn test-client-krn})
            response (handler update-request)
            body (test-utils/parse-json-response response)]
        (is (= 200 (:status response)))
        (is (= 200 (:total body)))
        
        ;; Verify total was added
        (let [saved (db/find-progress @test-db test-client-krn "add-total.csv" email)]
          (is (= 200 (:total saved))))))
    
    (testing "Create progress with errors and warnings"
      (let [request (test-utils/make-test-request
                     {:filename "errors-test.csv"
                      :counts {:done 50 :warn 2 :failed 3}
                      :errors [{:line 10 :message "Invalid date format"}
                               {:line 25 :message "Missing required field"}
                               {:line 30 :message "Duplicate entry"}]
                      :warnings [{:line 15 :message "Deprecated field used"}
                                 {:line 40 :message "Value exceeds recommended range"}]}
                     :identity identity
                     :path-params {:clientKrn test-client-krn})
            response (handler request)
            body (test-utils/parse-json-response response)]
        (is (= 200 (:status response)))
        (is (= 3 (count (:errors body))))
        (is (= 2 (count (:warnings body))))
        
        ;; Verify specific error details with pattern structure
        (let [first-error (first (:errors body))]
          (is (= 10 (get-in first-error [:lines 0 :line])))
          (is (= "Invalid date format" (:pattern first-error))))
        
        ;; Verify in database
        (let [saved (db/find-progress @test-db test-client-krn "errors-test.csv" email)]
          (is (= 3 (count (:errors saved))))
          (is (= 2 (count (:warnings saved)))))))
    
    (testing "Append errors and warnings on update"
      (let [;; First create with initial errors
            _ (handler (test-utils/make-test-request
                        {:filename "append-errors.csv"
                         :counts {:done 10 :warn 1 :failed 1}
                         :errors [{:line 5 :message "Initial error"}]
                         :warnings [{:line 3 :message "Initial warning"}]}
                        :identity identity
                        :path-params {:clientKrn test-client-krn}))
            ;; Then update with additional errors and warnings
            update-request (test-utils/make-test-request
                            {:filename "append-errors.csv"
                             :counts {:done 20 :warn 1 :failed 2}
                             :errors [{:line 50 :message "New error"}
                                      {:line 75 :message "Another error"}]
                             :warnings [{:line 60 :message "New warning"}]}
                            :identity identity
                            :path-params {:clientKrn test-client-krn})
            response (handler update-request)
            body (test-utils/parse-json-response response)]
        (is (= 200 (:status response)))
        ;; After pattern matching consolidation:
        ;; - Initial error creates 1 pattern group
        ;; - New errors match against existing patterns and merge
        ;; - Result should be 3 distinct patterns (Initial, New, Another)
        (is (<= 3 (count (:errors body))))
        (is (<= 2 (count (:warnings body))))
        
        ;; Verify all error lines are preserved across both updates
        (let [all-error-lines (set (mapcat (fn [error-group]
                                            (map :line (:lines error-group)))
                                          (:errors body)))]
          (is (contains? all-error-lines 5))   ; Initial error
          (is (contains? all-error-lines 50))  ; New error
          (is (contains? all-error-lines 75))) ; Another error
        
        ;; Verify all warning lines are preserved
        (let [all-warning-lines (set (mapcat (fn [warning-group]
                                              (map :line (:lines warning-group)))
                                            (:warnings body)))]
          (is (contains? all-warning-lines 3))   ; Initial warning
          (is (contains? all-warning-lines 60)))))))  ; New warning

(deftest get-progress-handler-test
  (let [update-handler (handlers/update-progress-handler @test-db)
        get-handler (handlers/get-progress-handler @test-db)
        email "test@example.com"
        identity {:email email}]
    
    ;; Setup: Create some progress records
    (update-handler (test-utils/make-test-request
                    {:filename "file1.csv"
                     :counts {:done 50 :warn 5 :failed 2}
                     :total 100}
                    :identity identity
                    :path-params {:clientKrn test-client-krn}))
    (update-handler (test-utils/make-test-request
                    {:filename "file2.csv"
                     :counts {:done 30 :warn 0 :failed 0}
                     :total 50}
                    :identity identity
                    :path-params {:clientKrn test-client-krn}))
    
    (testing "Get specific file progress"
      (let [request {:path-params {:clientKrn test-client-krn}
                     :query-params {:filename "file1.csv"}}
            response (get-handler request)
            body (test-utils/parse-json-response response)]
        (is (= 200 (:status response)))
        (is (= "file1.csv" (:filename body)))
        (is (= {:done 50 :warn 5 :failed 2} (:counts body)))
        (is (= 100 (:total body)))
        (is (contains? body :id))))
    
    (testing "Get all progress for specific user by email"
      (let [request {:path-params {:clientKrn test-client-krn}
                     :query-params {:email email}}
            response (get-handler request)
            body (test-utils/parse-json-response response)]
        (is (= 200 (:status response)))
        (is (= 2 (count body)))
        (is (every? #(contains? % :id) body))
        (is (every? #(= email (:email %)) body))))
    
    (testing "Get all progress for client"
      (let [request {:path-params {:clientKrn test-client-krn}}
            response (get-handler request)
            body (test-utils/parse-json-response response)]
        (is (= 200 (:status response)))
        (is (>= (count body) 2)) ; At least the 2 we created
        (is (every? #(contains? % :id) body))))
    
    (testing "Missing clientKrn returns 400"
      (let [request {:query-params {:filename "file1.csv"}}
            response (get-handler request)
            body (test-utils/parse-json-response response)]
        (is (= 400 (:status response)))
        (is (= "clientKrn path parameter is required" (:error body)))))
    
    (testing "Progress not found for filename"
      (let [request {:path-params {:clientKrn test-client-krn}
                     :query-params {:filename "nonexistent.csv"}}
            response (get-handler request)
            body (test-utils/parse-json-response response)]
        (is (= 404 (:status response)))
        (is (= "Progress not found for this file" (:error body)))))
    
    (testing "Can access any user's progress by filename within same client"
      (let [other-email "other@example.com"
            other-identity {:email other-email}
            ;; Create progress for other user in same client
            _ (update-handler (test-utils/make-test-request
                              {:filename "other-file.csv"
                               :counts {:done 10 :warn 0 :failed 0}}
                              :identity other-identity
                              :path-params {:clientKrn test-client-krn}))
            ;; Access it by filename within same client
            request {:path-params {:clientKrn test-client-krn}
                     :query-params {:filename "other-file.csv"}}
            response (get-handler request)
            body (test-utils/parse-json-response response)]
        (is (= 200 (:status response)))
        (is (= "other-file.csv" (:filename body)))
        (is (= other-email (:email body)))))
    
    (testing "Cannot access progress from different client"
      (let [other-client-krn "krn:clnt:other-client"
            request {:path-params {:clientKrn other-client-krn}
                     :query-params {:filename "file1.csv"}}
            response (get-handler request)
            body (test-utils/parse-json-response response)]
        (is (= 404 (:status response)))
        (is (= "Progress not found for this file" (:error body)))))))

(deftest authorization-test
  (testing "Only original creator can update file progress within same client"
    (let [handler (handlers/update-progress-handler @test-db)
          creator-email "creator@example.com"
          creator-identity {:email creator-email}
          other-email "other@example.com"
          other-identity {:email other-email}]
      
      ;; Creator creates initial progress
      (testing "Creator can create initial progress"
        (let [request (test-utils/make-test-request
                       {:filename "restricted-file.csv"
                        :counts {:done 10 :warn 0 :failed 0}
                        :total 100}
                       :identity creator-identity
                       :path-params {:clientKrn test-client-krn})
              response (handler request)
              body (test-utils/parse-json-response response)]
          (is (= 200 (:status response)))
          (is (= "created" (:result body)))
          (is (= "restricted-file.csv" (:filename body)))))
      
      ;; Creator can update their own progress
      (testing "Creator can update their own progress"
        (let [request (test-utils/make-test-request
                       {:filename "restricted-file.csv"
                        :counts {:done 20 :warn 1 :failed 0}}
                       :identity creator-identity
                       :path-params {:clientKrn test-client-krn})
              response (handler request)
              body (test-utils/parse-json-response response)]
          (is (= 200 (:status response)))
          (is (= "updated" (:result body)))
          (is (= {:done 30 :warn 1 :failed 0} (:counts body)))))
      
      ;; Other user cannot update the file
      (testing "Other user gets 403 when trying to update"
        (let [request (test-utils/make-test-request
                       {:filename "restricted-file.csv"
                        :counts {:done 5 :warn 0 :failed 1}}
                       :identity other-identity
                       :path-params {:clientKrn test-client-krn})
              response (handler request)
              body (test-utils/parse-json-response response)]
          (is (= 403 (:status response)))
          (is (= "Only the original creator can update this file's progress" (:error body)))))
      
      ;; Verify the file progress wasn't changed by unauthorized user
      (testing "File progress unchanged after unauthorized attempt"
        (let [saved (db/find-progress @test-db test-client-krn "restricted-file.csv" creator-email)]
          (is (= {:done 30 :warn 1 :failed 0} (:counts saved))))))))