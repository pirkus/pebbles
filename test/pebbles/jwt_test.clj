(ns pebbles.jwt-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [pebbles.jwt :as jwt]))

(deftest auth-interceptor-test
  (testing "Missing Authorization header"
    (let [context {:request {:headers {}}}
          result ((:enter jwt/auth-interceptor) context)]
      (is (= 401 (get-in result [:response :status])))
      (is (= "Missing Authorization header" (get-in result [:response :body])))))
  
  (testing "Invalid Authorization header format"
    (let [context {:request {:headers {"authorization" "InvalidFormat"}}}
          result ((:enter jwt/auth-interceptor) context)]
      (is (= 401 (get-in result [:response :status])))
      (is (= "Missing Authorization header" (get-in result [:response :body])))))
  
  (testing "Invalid JWT token"
    ;; Use a properly formatted (but invalid) JWT token to avoid format validation errors
    (let [context {:request {:headers {"authorization" "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.invalid_signature"}}}
          result ((:enter jwt/auth-interceptor) context)]
      (is (= 401 (get-in result [:response :status])))
      (is (= "Invalid or expired token" (get-in result [:response :body])))))
  
  ;; Note: Testing with valid JWT would require mocking or a test JWT service
  (testing "JWT verification function with invalid token"
    ;; Use a properly formatted (but invalid) JWT token
    (let [result (jwt/verify-google-jwt "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.invalid_signature")]
      (is (nil? result)))))