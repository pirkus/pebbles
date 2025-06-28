(ns pebbles.interceptors-integration-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [io.pedestal.interceptor :as interceptor]
   [io.pedestal.interceptor.chain :as chain]
   [cheshire.core :as json]
   [pebbles.interceptors :as interceptors]))

(defn make-throwing-interceptor
  "Creates an interceptor that throws a specific exception"
  [name exception]
  (interceptor/interceptor
   {:name name
    :enter (fn [_] (throw exception))}))

(deftest exception-handler-integration-test
  (testing "JsonEOFException is handled correctly in interceptor chain"
    (let [interceptor-chain [interceptors/exception-handler
                           (make-throwing-interceptor 
                            ::json-thrower
                            (com.fasterxml.jackson.core.io.JsonEOFException. 
                             nil nil "Bad JSON"))]
          context {:request {}}
          result (chain/execute context interceptor-chain)
          response (:response result)
          body (json/parse-string (:body response) true)]
      (is (= 400 (:status response)))
      (is (= "Validation error: Bad JSON" (:error body)))))
  
  (testing "MongoException is handled correctly in interceptor chain"
    (let [interceptor-chain [interceptors/exception-handler
                           (make-throwing-interceptor 
                            ::mongo-thrower
                            (com.mongodb.MongoException. "Database connection failed"))]
          context {:request {}}
          result (chain/execute context interceptor-chain)
          response (:response result)
          body (json/parse-string (:body response) true)]
      (is (= 500 (:status response)))
      (is (re-find #"Database error" (:error body)))))
  
  (testing "Generic exceptions are handled correctly in interceptor chain"
    (let [interceptor-chain [interceptors/exception-handler
                           (make-throwing-interceptor 
                            ::generic-thrower
                            (RuntimeException. "Something went wrong"))]
          context {:request {}}
          result (chain/execute context interceptor-chain)
          response (:response result)
          body (json/parse-string (:body response) true)]
      (is (= 500 (:status response)))
      (is (re-find #"Something went wrong" (:error body)))))
  
  (testing "Exception handler doesn't interfere with normal execution"
    (let [normal-interceptor (interceptor/interceptor
                              {:name ::normal
                               :enter (fn [context] 
                                        (assoc context :result :success))})
          interceptor-chain [interceptors/exception-handler
                           normal-interceptor]
          context {:request {}}
          result (chain/execute context interceptor-chain)]
      (is (= :success (:result result)))
      (is (nil? (:response result))))))