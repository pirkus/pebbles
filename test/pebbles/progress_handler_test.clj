(ns pebbles.progress-handler-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [pebbles.test-handlers :as handlers]
   [pebbles.test-utils :as test-utils]
   [pebbles.mock-db :as db]))

(def test-db (atom nil))

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
        client-krn "krn:clnt:test-client"
        email "test@example.com"
        identity {:email email}]
    
    (testing "Create new progress"
      (let [request (test-utils/make-test-request
                     {:filename "test-file.csv"
                      :counts {:done 10 :warn 2 :failed 1}
                      :total 100}
                     :identity identity
                     :path-params {:client-krn client-krn})
            response (handler request)
            body (test-utils/parse-json-response response)]
        (is (= 200 (:status response)))
        (is (= "created" (:result body)))
        (is (= client-krn (:clientKrn body)))
        (is (= "test-file.csv" (:filename body)))
        (is (= {:done 10 :warn 2 :failed 1} (:counts body)))
        (is (= 100 (:total body)))
        (is (false? (:isCompleted body)))
        
        ;; Verify in database
        (let [saved (db/find-progress @test-db client-krn "test-file.csv" email)]
          (is (= client-krn (:clientKrn saved)))
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
                        :path-params {:client-krn client-krn}))
            ;; Then update it
            update-request (test-utils/make-test-request
                            {:filename "update-test.csv"
                             :counts {:done 10 :warn 1 :failed 0}}
                            :identity identity
                            :path-params {:client-krn client-krn})
            response (handler update-request)
            body (test-utils/parse-json-response response)]
        (is (= 200 (:status response)))
        (is (= "updated" (:result body)))
        (is (= {:done 15 :warn 1 :failed 0} (:counts body)))
        
        ;; Verify counts were added, not replaced
        (let [saved (db/find-progress @test-db client-krn "update-test.csv" email)]
          (is (= {:done 15 :warn 1 :failed 0} (:counts saved))))))
    
    (testing "Complete progress with isLast flag"
      (let [request (test-utils/make-test-request
                     {:filename "complete-test.csv"
                      :counts {:done 100 :warn 0 :failed 0}
                      :total 100
                      :isLast true}
                     :identity identity
                     :path-params {:client-krn client-krn})
            response (handler request)
            body (test-utils/parse-json-response response)]
        (is (= 200 (:status response)))
        (is (true? (:isCompleted body)))
        
        ;; Verify in database
        (let [saved (db/find-progress @test-db client-krn "complete-test.csv" email)]
          (is (true? (:isCompleted saved))))))
    
    (testing "Reject update to completed progress"
      (let [;; First create and complete a progress
            _ (handler (test-utils/make-test-request
                        {:filename "locked-test.csv"
                         :counts {:done 100 :warn 0 :failed 0}
                         :isLast true}
                        :identity identity
                        :path-params {:client-krn client-krn}))
            ;; Try to update it
            update-request (test-utils/make-test-request
                            {:filename "locked-test.csv"
                             :counts {:done 10 :warn 0 :failed 0}}
                            :identity identity
                            :path-params {:client-krn client-krn})
            response (handler update-request)
            body (test-utils/parse-json-response response)]
        (is (= 400 (:status response)))
        (is (= "This file processing has already been completed" (:error body)))))
    
    (testing "Missing email in identity"
      (let [request (test-utils/make-test-request
                     {:filename "no-auth.csv"
                      :counts {:done 1 :warn 0 :failed 0}}
                     :identity {}
                     :path-params {:client-krn client-krn})
            response (handler request)
            body (test-utils/parse-json-response response)]
        (is (= 403 (:status response)))
        (is (= "No email found in authentication token" (:error body)))))
    
    (testing "Add total to existing progress"
      (let [;; Create without total
            _ (handler (test-utils/make-test-request
                        {:filename "add-total.csv"
                         :counts {:done 5 :warn 0 :failed 0}}
                        :identity identity
                        :path-params {:client-krn client-krn}))
            ;; Update with total
            update-request (test-utils/make-test-request
                            {:filename "add-total.csv"
                             :counts {:done 5 :warn 0 :failed 0}
                             :total 200}
                            :identity identity
                            :path-params {:client-krn client-krn})
            response (handler update-request)
            body (test-utils/parse-json-response response)]
        (is (= 200 (:status response)))
        (is (= 200 (:total body)))
        
        ;; Verify total was added
        (let [saved (db/find-progress @test-db client-krn "add-total.csv" email)]
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
                     :path-params {:client-krn client-krn})
            response (handler request)
            body (test-utils/parse-json-response response)]
        (is (= 200 (:status response)))
        (is (= 3 (count (:errors body))))
        (is (= 2 (count (:warnings body))))
        
        ;; Verify specific error details
        (let [first-error (first (:errors body))]
          (is (= 10 (:line first-error)))
          (is (= "Invalid date format" (:message first-error))))
        
        ;; Verify in database
        (let [saved (db/find-progress @test-db client-krn "errors-test.csv" email)]
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
                        :path-params {:client-krn client-krn}))
            ;; Then update with additional errors and warnings
            update-request (test-utils/make-test-request
                            {:filename "append-errors.csv"
                             :counts {:done 20 :warn 1 :failed 2}
                             :errors [{:line 50 :message "New error"}
                                      {:line 75 :message "Another error"}]
                             :warnings [{:line 60 :message "New warning"}]}
                            :identity identity
                            :path-params {:client-krn client-krn})
            response (handler update-request)
            body (test-utils/parse-json-response response)]
        (is (= 200 (:status response)))
        (is (= 3 (count (:errors body)))) ; 1 initial + 2 new
        (is (= 2 (count (:warnings body)))) ; 1 initial + 1 new
        
        ;; Verify all errors are preserved in order
        (let [errors (:errors body)]
          (is (= 5 (:line (first errors))))
          (is (= 50 (:line (second errors))))
          (is (= 75 (:line (nth errors 2)))))
        
        ;; Verify in database
        (let [saved (db/find-progress @test-db client-krn "append-errors.csv" email)]
          (is (= 3 (count (:errors saved))))
          (is (= 2 (count (:warnings saved)))))))
    
    (testing "Empty errors and warnings arrays are handled"
      (let [request (test-utils/make-test-request
                     {:filename "empty-arrays.csv"
                      :counts {:done 100 :warn 0 :failed 0}
                      :errors []
                      :warnings []}
                     :identity identity
                     :path-params {:client-krn client-krn})
            response (handler request)
            body (test-utils/parse-json-response response)]
        (is (= 200 (:status response)))
        (is (= [] (:errors body)))
        (is (= [] (:warnings body)))))))

(deftest get-progress-handler-test
  (let [update-handler (handlers/update-progress-handler @test-db)
        get-handler (handlers/get-progress-handler @test-db)
        client-krn "krn:clnt:test-client"
        email "test@example.com"
        identity {:email email}]
    
    ;; Setup: Create some progress records
    (update-handler (test-utils/make-test-request
                    {:filename "file1.csv"
                     :counts {:done 50 :warn 5 :failed 2}
                     :total 100}
                    :identity identity
                    :path-params {:client-krn client-krn}))
    (update-handler (test-utils/make-test-request
                    {:filename "file2.csv"
                     :counts {:done 30 :warn 0 :failed 0}
                     :total 50}
                    :identity identity
                    :path-params {:client-krn client-krn}))
    
    (testing "Get specific file progress"
      (let [request {:query-params {:filename "file1.csv"}
                     :path-params {:client-krn client-krn}}
            response (get-handler request)
            body (test-utils/parse-json-response response)]
        (is (= 200 (:status response)))
        (is (= client-krn (:clientKrn body)))
        (is (= "file1.csv" (:filename body)))
        (is (= {:done 50 :warn 5 :failed 2} (:counts body)))
        (is (= 100 (:total body)))
        (is (contains? body :id))))
    
    (testing "Get all progress for specific user by email"
      (let [request {:query-params {:email email}
                     :path-params {:client-krn client-krn}}
            response (get-handler request)
            body (test-utils/parse-json-response response)]
        (is (= 200 (:status response)))
        (is (= 2 (count body)))
        (is (every? #(contains? % :id) body))
        (is (every? #(= email (:email %)) body))
        (is (every? #(= client-krn (:clientKrn %)) body))))
    
    (testing "Get all progress for client"
      (let [request {:query-params {}
                     :path-params {:client-krn client-krn}}
            response (get-handler request)
            body (test-utils/parse-json-response response)]
        (is (= 200 (:status response)))
        (is (>= (count body) 2)) ; At least the 2 we created
        (is (every? #(contains? % :id) body))
        (is (every? #(= client-krn (:clientKrn %)) body))))
    
    (testing "Progress not found for filename"
      (let [request {:query-params {:filename "nonexistent.csv"}
                     :path-params {:client-krn client-krn}}
            response (get-handler request)
            body (test-utils/parse-json-response response)]
        (is (= 404 (:status response)))
        (is (= "Progress not found for this file" (:error body)))))
    
    (testing "Can access any user's progress by filename"
      (let [other-email "other@example.com"
            other-identity {:email other-email}
            ;; Create progress for other user
            _ (update-handler (test-utils/make-test-request
                              {:filename "other-file.csv"
                               :counts {:done 10 :warn 0 :failed 0}}
                              :identity other-identity
                              :path-params {:client-krn client-krn}))
            ;; Access it without authentication by filename
            request {:query-params {:filename "other-file.csv"}
                     :path-params {:client-krn client-krn}}
            response (get-handler request)
            body (test-utils/parse-json-response response)]
        (is (= 200 (:status response)))
        (is (= "other-file.csv" (:filename body)))
        (is (= other-email (:email body))))))
  
  (testing "Multi-tenancy isolation"
    (let [update-handler (handlers/update-progress-handler @test-db)
          get-handler (handlers/get-progress-handler @test-db)
          client-krn1 "krn:clnt:client-1"
          client-krn2 "krn:clnt:client-2"
          email "test@example.com"
          identity {:email email}]
      
      ;; Create progress for client 1
      (update-handler (test-utils/make-test-request
                      {:filename "shared-name.csv"
                       :counts {:done 100 :warn 0 :failed 0}}
                      :identity identity
                      :path-params {:client-krn client-krn1}))
      
      ;; Create progress for client 2 with same filename
      (update-handler (test-utils/make-test-request
                      {:filename "shared-name.csv"
                       :counts {:done 200 :warn 5 :failed 2}}
                      :identity identity
                      :path-params {:client-krn client-krn2}))
      
      ;; Query client 1's progress
      (let [request {:query-params {:filename "shared-name.csv"}
                     :path-params {:client-krn client-krn1}}
            response (get-handler request)
            body (test-utils/parse-json-response response)]
        (is (= 200 (:status response)))
        (is (= {:done 100 :warn 0 :failed 0} (:counts body))))
      
      ;; Query client 2's progress
      (let [request {:query-params {:filename "shared-name.csv"}
                     :path-params {:client-krn client-krn2}}
            response (get-handler request)
            body (test-utils/parse-json-response response)]
        (is (= 200 (:status response)))
        (is (= {:done 200 :warn 5 :failed 2} (:counts body)))))))

(deftest authorization-test
  (testing "Only original creator can update file progress"
    (let [handler (handlers/update-progress-handler @test-db)
          client-krn "krn:clnt:test-client"
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
                       :path-params {:client-krn client-krn})
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
                       :path-params {:client-krn client-krn})
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
                       :path-params {:client-krn client-krn})
              response (handler request)
              body (test-utils/parse-json-response response)]
          (is (= 403 (:status response)))
          (is (= "Only the original creator can update this file's progress" (:error body)))))
      
      ;; Verify the file progress wasn't changed by unauthorized user
      (testing "File progress unchanged after unauthorized attempt"
        (let [saved (db/find-progress @test-db client-krn "restricted-file.csv" creator-email)]
          (is (= {:done 30 :warn 1 :failed 0} (:counts saved))))))))