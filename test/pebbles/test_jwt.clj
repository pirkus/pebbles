(ns pebbles.test-jwt
  "Test version of JWT that doesn't make network calls"
  (:require [clojure.string :as str]))

(defn verify-google-jwt [token]
  ;; For testing, we'll accept a specific test token
  (when (= token "test-valid-token")
    {:email "test@example.com"
     :sub "123456789"
     :name "Test User"}))

(def auth-interceptor
  {:name ::auth
   :enter
   (fn [context]
     (let [authz (get-in context [:request :headers "authorization"])]
       (if (and authz (str/starts-with? authz "Bearer "))
         (let [token (subs authz 7)
               claims (verify-google-jwt token)]
           (if claims
             (assoc-in context [:request :identity] claims)
             (assoc context :response {:status 401 :body "Invalid or expired token"})))
         (assoc context :response {:status 401 :body "Missing Authorization header"}))))})