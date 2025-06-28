(ns pebbles.jwt
  (:require [clojure.string :as str]
            [com.github.sikt-no.clj-jwt :as clj-jwt]
            [clojure.tools.logging :as log]))

(def google-jwks-url "https://www.googleapis.com/oauth2/v3/certs")

;; Test mode flag - when true, skip actual JWT verification
(def ^:dynamic *test-mode* false)

(defn verify-google-jwt [token]
  (if *test-mode*
    ;; In test mode, return nil for any token (simulating invalid token)
    nil
    ;; Production mode - actual verification
    (try
      (clj-jwt/unsign google-jwks-url token)
      (catch Exception e
        (log/debug "JWT verification failed:" e)
        nil))))

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