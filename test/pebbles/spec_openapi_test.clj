(ns pebbles.spec-openapi-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.spec.alpha :as s]
            [pebbles.spec-openapi :as spec-openapi]
            [pebbles.specs :as specs]))

(deftest test-basic-spec-conversion
  (testing "Basic type conversions"
    (is (= {:type "string"} (spec-openapi/spec->openapi 'string?)))
    (is (= {:type "integer"} (spec-openapi/spec->openapi 'integer?)))
    (is (= {:type "boolean"} (spec-openapi/spec->openapi 'boolean?)))))

(deftest test-constraint-conversion
  (testing "Numeric constraints"
    (let [spec-form (s/form ::specs/done)
          schema (spec-openapi/spec->openapi spec-form)]
      (is (= "integer" (:type schema)))
      (is (= 0 (:minimum schema))))
    
    (let [spec-form (s/form ::specs/line)
          schema (spec-openapi/spec->openapi spec-form)]
      (is (= "integer" (:type schema)))
      (is (= 1 (:minimum schema))))))

(deftest test-object-conversion
  (testing "Keys spec conversion"
    (let [spec-form (s/form ::specs/counts)
          schema (spec-openapi/spec->openapi spec-form)]
      (is (= "object" (:type schema)))
      (is (contains? (:properties schema) "done"))
      (is (contains? (:properties schema) "warn"))
      (is (contains? (:properties schema) "failed"))
      (is (= ["done" "warn" "failed"] (:required schema))))))

(deftest test-collection-conversion
  (testing "Collection spec conversion"
    (let [spec-form (s/form ::specs/errors-response)
          schema (spec-openapi/spec->openapi spec-form)]
      (is (= "array" (:type schema)))
      (is (contains? (:items schema) :$ref)))))

(deftest test-generated-schemas
  (testing "Generated OpenAPI schemas from specs"
    (let [schemas (spec-openapi/specs->openapi-schemas)]
      (is (map? schemas))
      (is (contains? schemas "counts"))
      (is (contains? schemas "error-detail-response"))
      (is (contains? schemas "progress-update-params"))
      (is (contains? schemas "progress-response"))
      (is (contains? schemas "progress-record"))
      
      ;; Check Counts schema
      (let [counts-schema (get schemas "counts")]
        (is (= "object" (:type counts-schema)))
        (is (= ["done" "warn" "failed"] (:required counts-schema))))
      
      ;; Check ProgressUpdate schema
      (let [update-schema (get schemas "progress-update-params")]
        (is (= "object" (:type update-schema)))
        (is (contains? (:properties update-schema) "filename"))
        (is (contains? (:properties update-schema) "counts"))))))