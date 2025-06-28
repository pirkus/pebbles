(ns pebbles.jwt-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [pebbles.jwt :as jwt]))

(defn test-mode-fixture [f]
  (binding [jwt/*test-mode* true]
    (f)))

(use-fixtures :each test-mode-fixture)

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
  
  (testing "Authorization header without Bearer prefix"
    (let [context {:request {:headers {"authorization" "SomeToken"}}}
          result ((:enter jwt/auth-interceptor) context)]
      (is (= 401 (get-in result [:response :status])))
      (is (= "Missing Authorization header" (get-in result [:response :body])))))
  
  (testing "Invalid JWT token"
    ;; In test mode, all tokens are treated as invalid
    (let [context {:request {:headers {"authorization" "Bearer invalid-token"}}}
          result ((:enter jwt/auth-interceptor) context)]
      (is (= 401 (get-in result [:response :status])))
      (is (= "Invalid or expired token" (get-in result [:response :body])))))
  
  (testing "Well-formed but invalid JWT token"
    ;; Even with properly formatted JWT, test mode returns invalid
    (let [context {:request {:headers {"authorization" "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.invalid_signature"}}}
          result ((:enter jwt/auth-interceptor) context)]
      (is (= 401 (get-in result [:response :status])))
      (is (= "Invalid or expired token" (get-in result [:response :body]))))))

(deftest verify-google-jwt-test
  (testing "JWT verification in test mode always returns nil"
    (let [fake-token "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.invalid_signature"]
      (is (nil? (jwt/verify-google-jwt fake-token)))))
  
  (testing "JWT verification with empty token"
    (is (nil? (jwt/verify-google-jwt ""))))
  
  (testing "JWT verification with nil token"
    (is (nil? (jwt/verify-google-jwt nil)))))