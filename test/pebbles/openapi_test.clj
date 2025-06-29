(ns pebbles.openapi-test
  (:require [clojure.test :refer [deftest is testing]]
            [pebbles.openapi :as openapi]
            [pebbles.handlers :as handlers]))

(deftest test-handler-metadata-extraction
  (testing "Handler metadata extraction"
    (let [handler (handlers/update-progress-handler nil)
          metadata (openapi/extract-openapi-metadata handler)]
      (is (not (nil? metadata)))
      (is (contains? metadata :post))
      (is (= "Update file processing progress" (get-in metadata [:post :summary]))))))

(deftest test-openapi-generation
  (testing "OpenAPI spec generation from routes"
    (let [test-routes [{:path "/progress/:clientKrn"
                        :method :post
                        :interceptors [(handlers/update-progress-handler nil)]
                        :path-params {:clientKrn :string}}
                       {:path "/progress/:clientKrn"
                        :method :get
                        :interceptors [(handlers/get-progress-handler nil)]
                        :path-params {:clientKrn :string}}
                       {:path "/health"
                        :method :get
                        :interceptors [(handlers/health-handler)]}]
          spec (openapi/generate-openapi-spec test-routes)]
      
      (is (= "3.0.0" (:openapi spec)))
      (is (contains? spec :paths))
      (is (contains? (:paths spec) "/progress/{clientKrn}"))
      (is (contains? (:paths spec) "/health"))
      
      ;; Check POST endpoint
      (let [progress-post (get-in spec [:paths "/progress/{clientKrn}" :post])]
        (is (= "Update file processing progress" (:summary progress-post)))
        (is (contains? progress-post :security))
        (is (contains? progress-post :requestBody)))
      
      ;; Check GET endpoint
      (let [progress-get (get-in spec [:paths "/progress/{clientKrn}" :get])]
        (is (= "Retrieve progress information" (:summary progress-get)))
        (is (contains? progress-get :parameters)))
      
      ;; Check schemas are included
      (is (contains? (get-in spec [:components :schemas]) "progress-update-params"))
      (is (contains? (get-in spec [:components :schemas]) "progress-record")))))