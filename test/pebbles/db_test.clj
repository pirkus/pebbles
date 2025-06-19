(ns pebbles.db-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [pebbles.db :as db]
   [pebbles.test-utils :as test-utils]
   [monger.collection :as mc]
   [monger.operators :refer :all]))

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
    (let [email "test@example.com"
          filename "test.csv"
          progress-data {:filename filename
                        :email email
                        :counts {:done 10 :warn 2 :failed 1}
                        :total 100}]
      ;; Insert test data
      (mc/insert @test-db "progress" progress-data)
      
      ;; Find it
      (let [result (db/find-progress @test-db filename email)]
        (is (= filename (:filename result)))
        (is (= email (:email result)))
        (is (= {:done 10 :warn 2 :failed 1} (:counts result)))
        (is (= 100 (:total result))))))
  
  (testing "Find non-existent progress"
    (let [result (db/find-progress @test-db "nonexistent.csv" "nobody@example.com")]
      (is (nil? result)))))

(deftest create-progress-test
  (testing "Create new progress"
    (let [progress-data {:filename "new.csv"
                        :email "test@example.com"
                        :counts {:done 5 :warn 0 :failed 0}
                        :isCompleted false
                        :createdAt "2024-01-01T00:00:00Z"}
          result (db/create-progress @test-db progress-data)]
      (is (contains? result :_id))
      (is (= "new.csv" (:filename result)))
      (is (= "test@example.com" (:email result)))
      
      ;; Verify it was saved
      (let [saved (db/find-progress @test-db "new.csv" "test@example.com")]
        (is (= (:filename progress-data) (:filename saved))))))
  
  (testing "Create progress with errors and warnings"
    (let [progress-data {:filename "errors.csv"
                        :email "test@example.com"
                        :counts {:done 5 :warn 2 :failed 1}
                        :errors [{:line 10 :message "Error 1"}
                                {:line 20 :message "Error 2"}]
                        :warnings [{:line 5 :message "Warning 1"}]
                        :isCompleted false
                        :createdAt "2024-01-01T00:00:00Z"}
          result (db/create-progress @test-db progress-data)]
      (is (contains? result :_id))
      (is (= 2 (count (:errors result))))
      (is (= 1 (count (:warnings result))))
      
      ;; Verify errors and warnings were saved
      (let [saved (db/find-progress @test-db "errors.csv" "test@example.com")]
        (is (= 2 (count (:errors saved))))
        (is (= "Error 1" (get-in saved [:errors 0 :message])))
        (is (= 10 (get-in saved [:errors 0 :line])))
        (is (= 1 (count (:warnings saved))))
        (is (= "Warning 1" (get-in saved [:warnings 0 :message])))))))

(deftest update-progress-test
  (testing "Update existing progress"
    (let [email "test@example.com"
          filename "update.csv"
          ;; Create initial progress
          _ (mc/insert @test-db "progress" 
                      {:filename filename
                       :email email
                       :counts {:done 10 :warn 0 :failed 0}
                       :isCompleted false})
          ;; Update it
          update-doc {$set {:counts {:done 20 :warn 1 :failed 0}
                           :isCompleted true}}]
      
      (db/update-progress @test-db filename email update-doc)
      
      ;; Verify update
      (let [updated (db/find-progress @test-db filename email)]
        (is (= {:done 20 :warn 1 :failed 0} (:counts updated)))
        (is (true? (:isCompleted updated)))))))

(deftest find-all-progress-test
  (testing "Find all progress for a user"
    (let [email "test@example.com"
          other-email "other@example.com"]
      ;; Insert test data
      (mc/insert @test-db "progress" 
                {:filename "file1.csv" :email email :counts {:done 10 :warn 0 :failed 0}})
      (mc/insert @test-db "progress" 
                {:filename "file2.csv" :email email :counts {:done 20 :warn 1 :failed 0}})
      (mc/insert @test-db "progress" 
                {:filename "other-file.csv" :email other-email :counts {:done 5 :warn 0 :failed 0}})
      
      ;; Find user's progress
      (let [results (db/find-all-progress @test-db email)]
        (is (= 2 (count results)))
        (is (every? #(= email (:email %)) results))
        (is (contains? (set (map :filename results)) "file1.csv"))
        (is (contains? (set (map :filename results)) "file2.csv"))
        (is (not (contains? (set (map :filename results)) "other-file.csv"))))))
  
  (testing "Find progress for user with no records"
    (let [results (db/find-all-progress @test-db "nobody@example.com")]
      (is (empty? results)))))

(deftest find-progress-by-filename-test
  (testing "Find progress by filename only"
    (let [email "creator@example.com"
          filename "test-file.csv"]
      ;; Insert test data
      (mc/insert @test-db "progress" 
                {:filename filename :email email :counts {:done 15 :warn 1 :failed 0}})
      
      ;; Find by filename only
      (let [result (db/find-progress-by-filename @test-db filename)]
        (is (not (nil? result)))
        (is (= filename (:filename result)))
        (is (= email (:email result)))
        (is (= {:done 15 :warn 1 :failed 0} (:counts result))))))
  
  (testing "Find non-existent file returns nil"
    (let [result (db/find-progress-by-filename @test-db "non-existent.csv")]
      (is (nil? result)))))