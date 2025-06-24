(ns pebbles.db-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [pebbles.db :as db]
   [pebbles.test-utils :as test-utils]
   [monger.collection :as mc]))

(def test-db (atom nil))

(defn db-fixture [f]
  (let [db-map (test-utils/fresh-db)]
    (reset! test-db (:db db-map))
    (try
      (f)
      (finally
        (test-utils/cleanup-db db-map)))))

(use-fixtures :each db-fixture)

(deftest find-progress-test
  (testing "Find existing progress"
    (let [client-krn "krn:clnt:test-client"
          email "test@example.com"
          filename "test.csv"
          progress-data {:clientKrn client-krn
                        :filename filename
                        :email email
                        :counts {:done 10 :warn 2 :failed 1}
                        :total 100}]
      ;; Insert test data
      (mc/insert @test-db "progress" progress-data)
      
      ;; Find it
      (let [result (db/find-progress @test-db client-krn filename email)]
        (is (= client-krn (:clientKrn result)))
        (is (= filename (:filename result)))
        (is (= email (:email result)))
        (is (= {:done 10 :warn 2 :failed 1} (:counts result)))
        (is (= 100 (:total result))))))
  
  (testing "Find non-existent progress"
    (let [result (db/find-progress @test-db "krn:clnt:test-client" "nonexistent.csv" "nobody@example.com")]
      (is (nil? result))))
  
  (testing "Find progress with wrong client"
    (let [client-krn "krn:clnt:test-client"
          other-client-krn "krn:clnt:other-client"
          email "test@example.com"
          filename "test.csv"
          progress-data {:clientKrn client-krn
                        :filename filename
                        :email email
                        :counts {:done 10 :warn 2 :failed 1}}]
      ;; Insert test data for one client
      (mc/insert @test-db "progress" progress-data)
      
      ;; Try to find with different client
      (let [result (db/find-progress @test-db other-client-krn filename email)]
        (is (nil? result))))))

(deftest create-progress-test
  (testing "Create new progress"
    (let [progress-data {:clientKrn "krn:clnt:test-client"
                        :filename "new.csv"
                        :email "test@example.com"
                        :counts {:done 5 :warn 0 :failed 0}
                        :isCompleted false
                        :createdAt "2024-01-01T00:00:00Z"}
          result (db/create-progress @test-db progress-data)]
      (is (contains? result :_id))
      (is (= "krn:clnt:test-client" (:clientKrn result)))
      (is (= "new.csv" (:filename result)))
      (is (= "test@example.com" (:email result)))
      
      ;; Verify it was saved
      (let [saved (db/find-progress @test-db "krn:clnt:test-client" "new.csv" "test@example.com")]
        (is (= (:clientKrn progress-data) (:clientKrn saved)))
        (is (= (:filename progress-data) (:filename saved))))))
  
  (testing "Create progress with errors and warnings"
    (let [progress-data {:clientKrn "krn:clnt:test-client"
                        :filename "errors.csv"
                        :email "test@example.com"
                        :counts {:done 5 :warn 2 :failed 1}
                        :errors [{:line 10 :message "Error 1"}
                                {:line 20 :message "Error 2"}]
                        :warnings [{:line 5 :message "Warning 1"}]
                        :isCompleted false
                        :createdAt "2024-01-01T00:00:00Z"}
          result (db/create-progress @test-db progress-data)]
      (is (contains? result :_id))
      (is (= "krn:clnt:test-client" (:clientKrn result)))
      (is (= 2 (count (:errors result))))
      (is (= 1 (count (:warnings result))))
      
      ;; Verify errors and warnings were saved
      (let [saved (db/find-progress @test-db "krn:clnt:test-client" "errors.csv" "test@example.com")]
        (is (= 2 (count (:errors saved))))
        (is (= "Error 1" (get-in saved [:errors 0 :message])))
        (is (= 10 (get-in saved [:errors 0 :line])))
        (is (= 1 (count (:warnings saved))))
        (is (= "Warning 1" (get-in saved [:warnings 0 :message])))))))

(deftest update-progress-test
  (testing "Update existing progress"
    (let [client-krn "krn:clnt:test-client"
          email "test@example.com"
          filename "update.csv"
          ;; Create initial progress
          _ (mc/insert @test-db "progress" 
                      {:clientKrn client-krn
                       :filename filename
                       :email email
                       :counts {:done 10 :warn 0 :failed 0}
                       :isCompleted false})
          ;; Update it
          update-doc {"$set" {:counts {:done 20 :warn 1 :failed 0}
                           :isCompleted true}}]
      
      (db/update-progress @test-db client-krn filename email update-doc)
      
      ;; Verify update
      (let [updated (db/find-progress @test-db client-krn filename email)]
        (is (= {:done 20 :warn 1 :failed 0} (:counts updated)))
        (is (true? (:isCompleted updated)))))))

(deftest find-all-progress-test
  (testing "Find all progress for a user within a client"
    (let [client-krn "krn:clnt:test-client"
          other-client-krn "krn:clnt:other-client"
          email "test@example.com"
          other-email "other@example.com"]
      ;; Insert test data for same client
      (mc/insert @test-db "progress" 
                {:clientKrn client-krn :filename "file1.csv" :email email :counts {:done 10 :warn 0 :failed 0}})
      (mc/insert @test-db "progress" 
                {:clientKrn client-krn :filename "file2.csv" :email email :counts {:done 20 :warn 1 :failed 0}})
      ;; Insert data for different client (same email)
      (mc/insert @test-db "progress" 
                {:clientKrn other-client-krn :filename "file3.csv" :email email :counts {:done 30 :warn 0 :failed 0}})
      ;; Insert data for different user (same client)
      (mc/insert @test-db "progress" 
                {:clientKrn client-krn :filename "other-file.csv" :email other-email :counts {:done 5 :warn 0 :failed 0}})
      
      ;; Find user's progress within client
      (let [results (db/find-all-progress @test-db client-krn email)]
        (is (= 2 (count results)))
        (is (every? #(= client-krn (:clientKrn %)) results))
        (is (every? #(= email (:email %)) results))
        (is (contains? (set (map :filename results)) "file1.csv"))
        (is (contains? (set (map :filename results)) "file2.csv"))
        (is (not (contains? (set (map :filename results)) "file3.csv"))) ; different client
        (is (not (contains? (set (map :filename results)) "other-file.csv"))))) ; different user
    
    ;; Test with other client
    (let [results (db/find-all-progress @test-db "krn:clnt:other-client" "test@example.com")]
      (is (= 1 (count results)))
      (is (= "file3.csv" (:filename (first results))))))
  
  (testing "Find progress for user with no records"
    (let [results (db/find-all-progress @test-db "krn:clnt:test-client" "nobody@example.com")]
      (is (empty? results)))))

(deftest find-all-progress-for-client-test
  (testing "Find all progress for a client"
    (let [client-krn "krn:clnt:test-client"
          other-client-krn "krn:clnt:other-client"
          email1 "user1@example.com"
          email2 "user2@example.com"]
      ;; Insert test data for client
      (mc/insert @test-db "progress" 
                {:clientKrn client-krn :filename "file1.csv" :email email1 :counts {:done 10 :warn 0 :failed 0}})
      (mc/insert @test-db "progress" 
                {:clientKrn client-krn :filename "file2.csv" :email email2 :counts {:done 20 :warn 1 :failed 0}})
      ;; Insert data for different client
      (mc/insert @test-db "progress" 
                {:clientKrn other-client-krn :filename "file3.csv" :email email1 :counts {:done 30 :warn 0 :failed 0}})
      
      ;; Find all progress for client
      (let [results (db/find-all-progress-for-client @test-db client-krn)]
        (is (= 2 (count results)))
        (is (every? #(= client-krn (:clientKrn %)) results))
        (is (contains? (set (map :filename results)) "file1.csv"))
        (is (contains? (set (map :filename results)) "file2.csv"))
        (is (not (contains? (set (map :filename results)) "file3.csv")))))) ; different client
  
  (testing "Find progress for client with no records"
    (let [results (db/find-all-progress-for-client @test-db "krn:clnt:empty-client")]
      (is (empty? results)))))

(deftest find-progress-by-filename-test
  (testing "Find progress by client and filename"
    (let [client-krn "krn:clnt:test-client"
          other-client-krn "krn:clnt:other-client"
          email "creator@example.com"
          filename "test-file.csv"]
      ;; Insert test data for client
      (mc/insert @test-db "progress" 
                {:clientKrn client-krn :filename filename :email email :counts {:done 15 :warn 1 :failed 0}})
      ;; Insert data for different client with same filename
      (mc/insert @test-db "progress" 
                {:clientKrn other-client-krn :filename filename :email email :counts {:done 25 :warn 0 :failed 0}})
      
      ;; Find by client and filename
      (let [result (db/find-progress-by-filename @test-db client-krn filename)]
        (is (not (nil? result)))
        (is (= client-krn (:clientKrn result)))
        (is (= filename (:filename result)))
        (is (= email (:email result)))
        (is (= {:done 15 :warn 1 :failed 0} (:counts result))))
      
      ;; Find by other client and same filename
      (let [result (db/find-progress-by-filename @test-db other-client-krn filename)]
        (is (not (nil? result)))
        (is (= other-client-krn (:clientKrn result)))
        (is (= {:done 25 :warn 0 :failed 0} (:counts result))))))
  
  (testing "Find non-existent file returns nil"
    (let [result (db/find-progress-by-filename @test-db "krn:clnt:test-client" "non-existent.csv")]
      (is (nil? result)))))