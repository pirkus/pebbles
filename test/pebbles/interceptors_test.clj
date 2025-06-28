(ns pebbles.interceptors-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [cheshire.core :as json]
   [pebbles.interceptors :as interceptors]))

(deftest validate-progress-update-test
  (let [interceptor (interceptors/validate-progress-update)]
    
    (testing "passes through valid parameters"
      (let [valid-params {:filename "test.csv"
                         :counts {:done 10 :warn 2 :failed 1}
                         :total 100
                         :isLast false}
            context {:request {:json-params valid-params}}
            result ((:enter interceptor) context)]
        (is (= context result))
        (is (nil? (:response result)))))
    
    (testing "returns bad request for invalid parameters"
      (let [invalid-params {:filename "test.csv"
                           :counts {:done 10}} ; missing warn and failed
            context {:request {:json-params invalid-params}}
            result ((:enter interceptor) context)
            body (json/parse-string (get-in result [:response :body]) true)]
        (is (= 400 (get-in result [:response :status])))
        (is (string? (:error body)))
        (is (re-find #"Invalid parameters" 
                     (:error body)))))
    
    (testing "validates optional parameters when present"
      (let [params-with-errors {:filename "test.csv"
                               :counts {:done 10 :warn 2 :failed 1}
                               :errors [{:line 5 :message "Error"}]
                               :warnings [{:line 10 :message "Warning"}]}
            context {:request {:json-params params-with-errors}}
            result ((:enter interceptor) context)]
        (is (= context result))))))

