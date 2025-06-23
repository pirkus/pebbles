(ns pebbles.system-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [pebbles.system :as system]
   [pebbles.specs :as specs]
   [clojure.spec.alpha :as s])
  (:import
   [pebbles.system MongoComponent HttpComponent]
   [pebbles.sqs_consumer SQSConsumerComponent]
   [pebbles.kafka_consumer KafkaConsumerComponent]))

(deftest health-handler-test
  (testing "Health endpoint returns OK"
    (let [handler (system/health-handler)
          response (handler {})]
      (is (= 200 (:status response)))
      (is (= "OK" (:body response))))))

(deftest validate-progress-update-test
  (testing "Validation interceptor with valid data"
    (let [interceptor (system/validate-progress-update)
          context {:request {:json-params {:filename "test.csv"
                                          :counts {:done 10 :warn 2 :failed 1}}}}
          result ((:enter interceptor) context)]
      ;; Valid data should pass through without response
      (is (nil? (:response result)))))
  
  (testing "Validation interceptor with invalid data"
    (let [interceptor (system/validate-progress-update)
          context {:request {:json-params {:filename "test.csv"
                                          ;; Missing counts
                                          }}}
          result ((:enter interceptor) context)]
      ;; Invalid data should return error response
      (is (= 400 (get-in result [:response :status]))))))

(deftest system-component-test
  (testing "System components can be created"
    (let [sys (system/system)]
      (is (contains? sys :mongo))
      (is (contains? sys :http))
      (is (instance? MongoComponent (:mongo sys)))
      (is (instance? HttpComponent (:http sys)))))
  
  (testing "System components with SQS enabled"
    (with-redefs [system/env (fn [k] (when (= k :sqs-enabled) "true"))]
      (let [sys (system/system)]
        (is (contains? sys :sqs-consumer))
        (is (instance? SQSConsumerComponent (:sqs-consumer sys))))))
  
  (testing "System components with Kafka enabled"
    (with-redefs [system/env (fn [k] (when (= k :kafka-enabled) "true"))]
      (let [sys (system/system)]
        (is (contains? sys :kafka-consumer))
        (is (instance? KafkaConsumerComponent (:kafka-consumer sys))))))
  
  (testing "System components with both consumers disabled"
    (with-redefs [system/env (fn [k] nil)]
      (let [sys (system/system)]
        (is (not (contains? sys :sqs-consumer)))
        (is (not (contains? sys :kafka-consumer)))))))

(deftest route-expansion-test
  (testing "Routes can be expanded without errors"
    ;; This just tests that route expansion doesn't throw
    (let [routes (system/make-routes nil)]
      (is (seq routes)))))

(deftest progress-update-specs-test
  (testing "Progress update parameter validation"
    ;; Valid specs
    (is (s/valid? ::specs/progress-update-params
                  {:filename "test.csv"
                   :counts {:done 10 :warn 2 :failed 1}}))
    
    (is (s/valid? ::specs/progress-update-params
                  {:filename "test.csv"
                   :counts {:done 0 :warn 0 :failed 0}
                   :total 100
                   :isLast true}))
    
    ;; Invalid specs
    (is (not (s/valid? ::specs/progress-update-params
                       {:filename "test.csv"
                        :counts {:done -1 :warn 0 :failed 0}})))
    
    (is (not (s/valid? ::specs/progress-update-params
                       {:counts {:done 10 :warn 2 :failed 1}})))
    
    (is (not (s/valid? ::specs/progress-update-params
                       {:filename "test.csv"})))))