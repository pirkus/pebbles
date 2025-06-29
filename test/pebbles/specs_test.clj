(ns pebbles.specs-test
   (:require
    [clojure.test :refer [deftest is testing]]
    [clojure.spec.alpha :as s]
    [pebbles.specs :as specs]))

 (deftest progress-update-specs-test
   (testing "Valid progress update params"
     (is (s/valid? ::specs/progress-update-params
                   {:filename "test.csv"
                    :counts {:done 10 :warn 2 :failed 1}}))

     (is (s/valid? ::specs/progress-update-params
                   {:filename "test.csv"
                    :counts {:done 0 :warn 0 :failed 0}
                    :total 100
                    :isLast true}))

     ;; With errors and warnings
     (is (s/valid? ::specs/progress-update-params
                   {:filename "test.csv"
                    :counts {:done 10 :warn 2 :failed 1}
                    :errors [{:line 10 :message "Error on line 10"}
                             {:line 25 :message "Another error"}]
                    :warnings [{:line 5 :message "Warning message"}]})))

   (testing "Invalid progress update params"
     ;; Missing filename
     (is (not (s/valid? ::specs/progress-update-params
                        {:counts {:done 10 :warn 2 :failed 1}})))

     ;; Missing counts
     (is (not (s/valid? ::specs/progress-update-params
                        {:filename "test.csv"})))

     ;; Invalid count values (negative)
     (is (not (s/valid? ::specs/progress-update-params
                        {:filename "test.csv"
                         :counts {:done -1 :warn 0 :failed 0}})))

     ;; Missing required count fields
     (is (not (s/valid? ::specs/progress-update-params
                        {:filename "test.csv"
                         :counts {:done 10 :warn 2}})))

     ;; Invalid total (zero or negative)
     (is (not (s/valid? ::specs/progress-update-params
                        {:filename "test.csv"
                         :counts {:done 10 :warn 0 :failed 0}
                         :total 0})))

     ;; Invalid isLast type
     (is (not (s/valid? ::specs/progress-update-params
                        {:filename "test.csv"
                         :counts {:done 10 :warn 0 :failed 0}
                         :isLast "true"})))

     ;; Invalid error format - missing message (message is required in error-detail-request)
     (is (not (s/valid? ::specs/progress-update-params
                        {:filename "test.csv"
                         :counts {:done 10 :warn 0 :failed 0}
                         :errors [{:line 10}]})))

     ;; Invalid error format - negative line number
     (is (not (s/valid? ::specs/progress-update-params
                        {:filename "test.csv"
                         :counts {:done 10 :warn 0 :failed 0}
                         :errors [{:line -1 :message "Invalid line"}]})))

     ;; Invalid warning format
     (is (not (s/valid? ::specs/progress-update-params
                        {:filename "test.csv"
                         :counts {:done 10 :warn 0 :failed 0}
                         :warnings [{:line 0 :message "Line cannot be zero"}]})))))



 (deftest client-krn-validation-test
   (testing "Valid client KRN formats (krn:clnt: prefix)"
     (is (s/valid? ::specs/client-krn "krn:clnt:abc123"))
     (is (s/valid? ::specs/client-krn "krn:clnt:some-opaque-string"))
     (is (s/valid? ::specs/client-krn "krn:clnt:client-with-dashes"))
     (is (s/valid? ::specs/client-krn "krn:clnt:client_with_underscores"))
     (is (s/valid? ::specs/client-krn "krn:clnt:123456789")))

     (testing "Invalid client KRN formats"
    (is (not (s/valid? ::specs/client-krn nil))) ; nil not allowed
    (is (not (s/valid? ::specs/client-krn 123))) ; must be string
    (is (not (s/valid? ::specs/client-krn "any-string"))) ; missing krn:clnt: prefix
    (is (not (s/valid? ::specs/client-krn "client-123")))))