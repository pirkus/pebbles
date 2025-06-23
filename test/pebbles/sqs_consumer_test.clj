(ns pebbles.sqs-consumer-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [pebbles.sqs-consumer :as sqs]
   [pebbles.test-utils :as test-utils]
   [pebbles.db :as db]))

(def test-db (atom nil))

(defn db-fixture [f]
  (let [db-map (test-utils/fresh-db)]
    (reset! test-db (:db db-map))
    (try
      (f)
      (finally
        (test-utils/cleanup-db db-map)))))

(use-fixtures :each db-fixture)

;; Mock SQS message for testing
(defrecord MockSqsMessage [body receiptHandle])

(deftest parse-message-test
  (testing "Parse valid SQS message"
    (let [message (->MockSqsMessage "{\"clientKrn\":\"krn:clnt:test-client\",
                                     \"email\":\"test@example.com\",
                                     \"filename\":\"test.csv\",
                                     \"counts\":{\"done\":10,\"warn\":2,\"failed\":1},
                                     \"total\":100}"
                                    "test-receipt-handle")
          result (sqs/parse-message message)]
      (is (= "krn:clnt:test-client" (:clientKrn result)))
      (is (= "test@example.com" (:email result)))
      (is (= "test.csv" (:filename result)))
      (is (= {:done 10 :warn 2 :failed 1} (:counts result)))
      (is (= 100 (:total result)))))
  
  (testing "Parse invalid JSON returns nil"
    (let [message (->MockSqsMessage "invalid json{" "test-receipt-handle")
          result (sqs/parse-message message)]
      (is (nil? result)))))

(deftest validate-message-test
  (testing "Valid message passes validation"
    (let [message-data {:clientKrn "krn:clnt:test-client"
                       :email "test@example.com"
                       :filename "test.csv"
                       :counts {:done 10 :warn 0 :failed 0}}]
      (is (true? (sqs/validate-message message-data)))))
  
  (testing "Missing clientKrn fails validation"
    (let [message-data {:email "test@example.com"
                       :filename "test.csv"
                       :counts {:done 10 :warn 0 :failed 0}}]
      (is (false? (sqs/validate-message message-data)))))
  
  (testing "Missing email fails validation"
    (let [message-data {:clientKrn "krn:clnt:test-client"
                       :filename "test.csv"
                       :counts {:done 10 :warn 0 :failed 0}}]
      (is (false? (sqs/validate-message message-data)))))
  
  (testing "Invalid counts fails validation"
    (let [message-data {:clientKrn "krn:clnt:test-client"
                       :email "test@example.com"
                       :filename "test.csv"
                       :counts {:done -5 :warn 0 :failed 0}}]
      (is (false? (sqs/validate-message message-data))))))

(deftest process-progress-update-test
  (testing "Create new progress from SQS message"
    (let [message-data {:clientKrn "krn:clnt:test-client"
                       :email "test@example.com"
                       :filename "new-file.csv"
                       :counts {:done 100 :warn 5 :failed 2}
                       :total 200}
          result (sqs/process-progress-update @test-db message-data)]
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
          _ (sqs/process-progress-update @test-db
                                        {:clientKrn client-krn
                                         :email email
                                         :filename filename
                                         :counts {:done 50 :warn 0 :failed 0}})
          ;; Update message
          update-data {:clientKrn client-krn
                      :email email
                      :filename filename
                      :counts {:done 75 :warn 3 :failed 1}}
          result (sqs/process-progress-update @test-db update-data)]
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
          _ (sqs/process-progress-update @test-db
                                        {:clientKrn client-krn
                                         :email creator-email
                                         :filename filename
                                         :counts {:done 100 :warn 0 :failed 0}})
          ;; Try to update with other user
          update-data {:clientKrn client-krn
                      :email other-email
                      :filename filename
                      :counts {:done 50 :warn 0 :failed 0}}
          result (sqs/process-progress-update @test-db update-data)]
      (is (= "Only the original creator can update this file's progress" (:error result)))
      
      ;; Verify original progress unchanged
      (let [saved (db/find-progress @test-db client-krn filename creator-email)]
        (is (= {:done 100 :warn 0 :failed 0} (:counts saved))))))
  
  (testing "Reject update to completed progress"
    (let [client-krn "krn:clnt:test-client"
          email "test@example.com"
          filename "completed.csv"
          ;; Create and complete
          _ (sqs/process-progress-update @test-db
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
          result (sqs/process-progress-update @test-db update-data)]
      (is (= "This file processing has already been completed" (:error result)))))
  
  (testing "Process message with errors and warnings"
    (let [message-data {:clientKrn "krn:clnt:test-client"
                       :email "test@example.com"
                       :filename "errors-file.csv"
                       :counts {:done 90 :warn 5 :failed 5}
                       :errors [{:line 10 :message "Invalid format"}
                               {:line 25 :message "Missing field"}]
                       :warnings [{:line 15 :message "Deprecated value"}]}
          result (sqs/process-progress-update @test-db message-data)]
      (is (= "created" (:result result)))
      (is (= 2 (count (get-in result [:progress :errors]))))
      (is (= 1 (count (get-in result [:progress :warnings]))))
      
      ;; Verify in database
      (let [saved (db/find-progress @test-db "krn:clnt:test-client" "errors-file.csv" "test@example.com")]
        (is (= 2 (count (:errors saved))))
        (is (= 1 (count (:warnings saved))))))))

(deftest process-message-test
  (testing "Process valid message successfully"
    (let [message (->MockSqsMessage "{\"clientKrn\":\"krn:clnt:test-client\",
                                     \"email\":\"test@example.com\",
                                     \"filename\":\"test-message.csv\",
                                     \"counts\":{\"done\":50,\"warn\":0,\"failed\":0}}"
                                   "test-receipt-handle")
          ;; Mock SQS client - track deletion
          deleted-messages (atom [])
          mock-sqs-client (reify software.amazon.awssdk.services.sqs.SqsClient
                           (^software.amazon.awssdk.services.sqs.model.DeleteMessageResponse deleteMessage [_ ^software.amazon.awssdk.services.sqs.model.DeleteMessageRequest request]
                             (swap! deleted-messages conj (.receiptHandle request))
                             (-> (software.amazon.awssdk.services.sqs.model.DeleteMessageResponse/builder)
                                 (.build))))
          queue-url "https://sqs.us-east-1.amazonaws.com/123456789/test-queue"
          result (sqs/process-message @test-db mock-sqs-client queue-url message)]
      (is (= "created" (:result result)))
      ;; Verify delete message was called
      (is (= ["test-receipt-handle"] @deleted-messages))))
  
  (testing "Invalid message is deleted to prevent reprocessing"
    (let [message (->MockSqsMessage "{\"invalid\":\"message\"}" "test-receipt-handle")
          deleted-messages (atom [])
          mock-sqs-client (reify software.amazon.awssdk.services.sqs.SqsClient
                           (^software.amazon.awssdk.services.sqs.model.DeleteMessageResponse deleteMessage [_ ^software.amazon.awssdk.services.sqs.model.DeleteMessageRequest request]
                             (swap! deleted-messages conj (.receiptHandle request))
                             (-> (software.amazon.awssdk.services.sqs.model.DeleteMessageResponse/builder)
                                 (.build))))
          queue-url "https://sqs.us-east-1.amazonaws.com/123456789/test-queue"
          result (sqs/process-message @test-db mock-sqs-client queue-url message)]
      (is (= "Invalid message format" (:error result)))
      ;; Verify delete was still called
      (is (= ["test-receipt-handle"] @deleted-messages))))
  
  (testing "Failed JSON parsing returns error"
    (let [message (->MockSqsMessage "not json at all" "test-receipt-handle")
          deleted-messages (atom [])
          mock-sqs-client (reify software.amazon.awssdk.services.sqs.SqsClient
                           (^software.amazon.awssdk.services.sqs.model.DeleteMessageResponse deleteMessage [_ ^software.amazon.awssdk.services.sqs.model.DeleteMessageRequest request]
                             (swap! deleted-messages conj (.receiptHandle request))
                             (-> (software.amazon.awssdk.services.sqs.model.DeleteMessageResponse/builder)
                                 (.build))))
          queue-url "https://sqs.us-east-1.amazonaws.com/123456789/test-queue"
          result (sqs/process-message @test-db mock-sqs-client queue-url message)]
      (is (= "Failed to parse message" (:error result)))
      ;; Verify delete was still called
      (is (= ["test-receipt-handle"] @deleted-messages)))))