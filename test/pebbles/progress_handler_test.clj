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
          (is (contains? all-warning-lines 60)))))  ; New warning
    
    (testing "Pattern data in request is treated as no-op - backend generates own patterns"
      (let [request (test-utils/make-test-request
                     {:filename "ignore-pattern-test.csv"
                      :counts {:done 10 :warn 1 :failed 1}
                      ;; Client sends pattern data - treated as no-op (ignored)
                      :errors [{:line 5 :message "Invalid date" :pattern "Client sent pattern"}]
                      :warnings [{:line 8 :message "Deprecated field" :pattern "Another client pattern"}]}
                     :identity identity
                     :path-params {:clientKrn test-client-krn})
            response (handler request)
            body (test-utils/parse-json-response response)]
        (is (= 200 (:status response)))
        (is (= 1 (count (:errors body))))
        (is (= 1 (count (:warnings body))))
        
        ;; Verify the backend generated its own patterns from the message field
        (let [error-pattern (:pattern (first (:errors body)))
              warning-pattern (:pattern (first (:warnings body)))]
          ;; Backend ignores client-sent pattern and generates own from message
          (is (= "Invalid date" error-pattern))
          (is (= "Deprecated field" warning-pattern)))))))

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

(deftest etag-integration-test
  (let [handler (handlers/get-progress-handler @test-db)]
    
    (testing "GET /progress/{clientKrn} returns ETag header"
      (let [test-progress {:clientKrn "krn:clnt:test-client"
                          :filename "test.csv"
                          :email "test@example.com"
                          :counts {:done 100 :warn 0 :failed 0}
                          :total 100
                          :isCompleted false
                          :createdAt "2024-01-01T00:00:00Z"
                          :updatedAt "2024-01-01T00:00:00Z"}]
        ;; Create test progress
        (db/create-progress @test-db test-progress)
        
        ;; First request should return 200 with ETag
        (let [request {:path-params {:clientKrn "krn:clnt:test-client"}
                       :headers {}}
              response (handler request)]
          (is (= 200 (:status response)))
          (is (contains? (:headers response) "ETag"))
          (is (= "no-cache" (get-in response [:headers "Cache-Control"])))
          (let [etag (get-in response [:headers "ETag"])]
            (is (string? etag))
            (is (re-matches #"\"[0-9a-f]{32}\"" etag))))))

    (testing "GET /progress/{clientKrn} returns 304 when ETag matches"
      (let [test-progress {:clientKrn "krn:clnt:test-client-2"
                          :filename "test2.csv"
                          :email "test2@example.com"
                          :counts {:done 50 :warn 5 :failed 1}
                          :total 100
                          :isCompleted false
                          :createdAt "2024-01-01T00:00:00Z"
                          :updatedAt "2024-01-01T00:00:00Z"}]
        ;; Create test progress
        (db/create-progress @test-db test-progress)
        
        ;; First request to get the ETag
        (let [request1 {:path-params {:clientKrn "krn:clnt:test-client-2"}
                        :headers {}}
              response1 (handler request1)
              etag (get-in response1 [:headers "ETag"])
          
          ;; Second request with If-None-Match should return 304
          request2 {:path-params {:clientKrn "krn:clnt:test-client-2"}
                          :headers {"if-none-match" etag}}
                response2 (handler request2)]
            (is (= 304 (:status response2)))
            (is (= etag (get-in response2 [:headers "ETag"])))
            (is (= "" (:body response2))))))

    (testing "GET /progress/{clientKrn}?filename=X returns ETag header"
      (let [test-progress {:clientKrn "krn:clnt:test-client-3"
                          :filename "specific.csv"
                          :email "test3@example.com"
                          :counts {:done 200 :warn 10 :failed 2}
                          :total 250
                          :isCompleted false
                          :createdAt "2024-01-01T00:00:00Z"
                          :updatedAt "2024-01-01T00:00:00Z"}]
        ;; Create test progress
        (db/create-progress @test-db test-progress)
        
        ;; Request specific file should return 200 with ETag
        (let [request {:path-params {:clientKrn "krn:clnt:test-client-3"}
                       :query-params {:filename "specific.csv"}
                       :headers {}}
              response (handler request)]
          (is (= 200 (:status response)))
          (is (contains? (:headers response) "ETag"))
          (is (= "no-cache" (get-in response [:headers "Cache-Control"]))))))))