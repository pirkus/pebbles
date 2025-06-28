(ns pebbles.system-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [pebbles.system :as system])
  (:import
   [pebbles.system MongoComponent HttpComponent]))

(deftest system-component-test
  (testing "System components can be created"
    (let [sys (system/system)]
      (is (contains? sys :mongo))
      (is (contains? sys :http))
      (is (instance? MongoComponent (:mongo sys)))
      (is (instance? HttpComponent (:http sys))))))

(deftest route-expansion-test
  (testing "Routes can be expanded without errors"
    ;; This just tests that route expansion doesn't throw
    (let [routes (system/make-routes nil)]
      (is (seq routes)))))