(ns pebbles.kafka-consumer-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [pebbles.test-kafka-consumer :as kafka]
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

;; Mock Kafka record for testing
(defrecord MockKafkaRecord [value])

(deftest parse-record-test
  (testing "Parse valid Kafka record"
    (let [record (->MockKafkaRecord "{\"clientKrn\":\"krn:clnt:test-client\",
                                     \"email\":\"test@example.com\",
                                     \"filename\":\"test.csv\",
                                     \"counts\":{\"done\":10,\"warn\":2,\"failed\":1},
                                     \"total\":100}")
          result (kafka/parse-record record)]
      (is (= "krn:clnt:test-client" (:clientKrn result)))
      (is (= "test@example.com" (:email result)))
      (is (= "test.csv" (:filename result)))
      (is (= {:done 10 :warn 2 :failed 1} (:counts result)))
      (is (= 100 (:total result)))))
  
  (testing "Parse invalid JSON returns nil"
    (let [record (->MockKafkaRecord "invalid json{")
          result (kafka/parse-record record)]
      (is (nil? result)))))

(deftest validate-record-test
  (testing "Valid record passes validation"
    (let [record-data {:clientKrn "krn:clnt:test-client"
                      :email "test@example.com"
                      :filename "test.csv"
                      :counts {:done 10 :warn 0 :failed 0}}]
      (is (true? (kafka/validate-record record-data)))))
  
  (testing "Missing clientKrn fails validation"
    (let [record-data {:email "test@example.com"
                      :filename "test.csv"
                      :counts {:done 10 :warn 0 :failed 0}}]
      (is (false? (kafka/validate-record record-data)))))
  
  (testing "Missing email fails validation"
    (let [record-data {:clientKrn "krn:clnt:test-client"
                      :filename "test.csv"
                      :counts {:done 10 :warn 0 :failed 0}}]
      (is (false? (kafka/validate-record record-data)))))
  
  (testing "Invalid counts fails validation"
    (let [record-data {:clientKrn "krn:clnt:test-client"
                      :email "test@example.com"
                      :filename "test.csv"
                      :counts {:done -5 :warn 0 :failed 0}}]
      (is (false? (kafka/validate-record record-data))))))

(deftest process-progress-update-test
  (testing "Create new progress from Kafka record"
    (let [record-data {:clientKrn "krn:clnt:test-client"
                      :email "test@example.com"
                      :filename "new-file.csv"
                      :counts {:done 100 :warn 5 :failed 2}
                      :total 200}
          result (kafka/process-progress-update @test-db record-data)]
      (is (= "created" (:result result)))
      (is (= "krn:clnt:test-client" (get-in result [:progress :clientKrn])))
      (is (= "new-file.csv" (get-in result [:progress :filename])))
      (is (= {:done 100 :warn 5 :failed 2} (get-in result [:progress :counts])))
      
      ;; Verify in database
      (let [saved (db/find-progress @test-db "krn:clnt:test-client" "new-file.csv" "test@example.com")]
        (is (not (nil? saved)))
        (is (= "krn:clnt:test-client" (:clientKrn saved)))
        (is (= {:done 100 :warn 5 :failed 2} (:counts saved))))))
  
  (testing "Update existing progress"
    (let [client-krn "krn:clnt:test-client"
          email "test@example.com"
          filename "update-file.csv"
          ;; Create initial progress
          _ (kafka/process-progress-update @test-db
                                          {:clientKrn client-krn
                                           :email email
                                           :filename filename
                                           :counts {:done 50 :warn 0 :failed 0}})
          ;; Update record
          update-data {:clientKrn client-krn
                      :email email
                      :filename filename
                      :counts {:done 75 :warn 3 :failed 1}}
          result (kafka/process-progress-update @test-db update-data)]
      (is (= "updated" (:result result)))
      (is (= {:done 125 :warn 3 :failed 1} (get-in result [:progress :counts])))
      
      ;; Verify in database
      (let [saved (db/find-progress @test-db client-krn filename email)]
        (is (= {:done 125 :warn 3 :failed 1} (:counts saved))))))
  
  (testing "Reject update from different user"
    (let [client-krn "krn:clnt:test-client"
          creator-email "creator@example.com"
          other-email "other@example.com"
          filename "restricted.csv"
          ;; Create with creator
          _ (kafka/process-progress-update @test-db
                                          {:clientKrn client-krn
                                           :email creator-email
                                           :filename filename
                                           :counts {:done 100 :warn 0 :failed 0}})
          ;; Try to update with other user
          update-data {:clientKrn client-krn
                      :email other-email
                      :filename filename
                      :counts {:done 50 :warn 0 :failed 0}}
          result (kafka/process-progress-update @test-db update-data)]
      (is (= "Only the original creator can update this file's progress" (:error result)))
      
      ;; Verify original progress unchanged
      (let [saved (db/find-progress @test-db client-krn filename creator-email)]
        (is (= {:done 100 :warn 0 :failed 0} (:counts saved))))))
  
  (testing "Reject update to completed progress"
    (let [client-krn "krn:clnt:test-client"
          email "test@example.com"
          filename "completed.csv"
          ;; Create and complete
          _ (kafka/process-progress-update @test-db
                                          {:clientKrn client-krn
                                           :email email
                                           :filename filename
                                           :counts {:done 100 :warn 0 :failed 0}
                                           :isLast true})
          ;; Try to update
          update-data {:clientKrn client-krn
                      :email email
                      :filename filename
                      :counts {:done 50 :warn 0 :failed 0}}
          result (kafka/process-progress-update @test-db update-data)]
      (is (= "This file processing has already been completed" (:error result)))))
  
  (testing "Process record with errors and warnings"
    (let [record-data {:clientKrn "krn:clnt:test-client"
                      :email "test@example.com"
                      :filename "errors-file.csv"
                      :counts {:done 90 :warn 5 :failed 5}
                      :errors [{:line 10 :message "Invalid format"}
                              {:line 25 :message "Missing field"}]
                      :warnings [{:line 15 :message "Deprecated value"}]}
          result (kafka/process-progress-update @test-db record-data)]
      (is (= "created" (:result result)))
      (is (= 2 (count (get-in result [:progress :errors]))))
      (is (= 1 (count (get-in result [:progress :warnings]))))
      
      ;; Verify in database
      (let [saved (db/find-progress @test-db "krn:clnt:test-client" "errors-file.csv" "test@example.com")]
        (is (= 2 (count (:errors saved))))
        (is (= 1 (count (:warnings saved))))))))

(deftest process-record-test
  (testing "Process valid record successfully"
    (let [record (->MockKafkaRecord "{\"clientKrn\":\"krn:clnt:test-client\",
                                     \"email\":\"test@example.com\",
                                     \"filename\":\"test-record.csv\",
                                     \"counts\":{\"done\":50,\"warn\":0,\"failed\":0}}")
          result (kafka/process-record @test-db record)]
      (is (= "created" (:result result)))
      
      ;; Verify in database
      (let [saved (db/find-progress @test-db "krn:clnt:test-client" "test-record.csv" "test@example.com")]
        (is (not (nil? saved)))
        (is (= {:done 50 :warn 0 :failed 0} (:counts saved))))))
  
  (testing "Invalid record returns error"
    (let [record (->MockKafkaRecord "{\"invalid\":\"record\"}")
          result (kafka/process-record @test-db record)]
      (is (= "Invalid record format" (:error result)))))
  
  (testing "Failed JSON parsing returns error"
    (let [record (->MockKafkaRecord "not json at all")
          result (kafka/process-record @test-db record)]
      (is (= "Failed to parse record" (:error result)))))

  (testing "Multi-tenancy isolation in Kafka consumer"
    (let [client-krn1 "krn:clnt:client-1"
          client-krn2 "krn:clnt:client-2"
          email "test@example.com"
          filename "shared-name.csv"
          ;; Create progress for client 1
          _ (kafka/process-progress-update @test-db
                                          {:clientKrn client-krn1
                                           :email email
                                           :filename filename
                                           :counts {:done 100 :warn 0 :failed 0}})
          ;; Create progress for client 2 with same filename
          _ (kafka/process-progress-update @test-db
                                          {:clientKrn client-krn2
                                           :email email
                                           :filename filename
                                           :counts {:done 200 :warn 5 :failed 2}})
          ;; Verify isolation
          client1-progress (db/find-progress @test-db client-krn1 filename email)
          client2-progress (db/find-progress @test-db client-krn2 filename email)]
      (is (= {:done 100 :warn 0 :failed 0} (:counts client1-progress)))
      (is (= {:done 200 :warn 5 :failed 2} (:counts client2-progress))))))