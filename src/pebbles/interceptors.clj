(ns pebbles.interceptors
  (:require
   [clojure.spec.alpha :as s]
   [clojure.tools.logging :as log]
   [io.pedestal.interceptor :as interceptor]
   [io.pedestal.interceptor.error :as err]
   [pebbles.http-resp :as http-resp]
   [pebbles.specs :as specs]))

(def exception-handler
  (err/error-dispatch [context ex]
    [{:exception-type :com.fasterxml.jackson.core.io.JsonEOFException}]
    (let [original-ex (:exception (ex-data ex))]
      (log/warn "JSON parsing error:" original-ex)
      (assoc context :response (http-resp/handle-validation-error original-ex)))

    [{:exception-type :com.mongodb.MongoException}]
    (let [original-ex (:exception (ex-data ex))]
      (log/error "MongoDB error:" original-ex)
      (assoc context :response (http-resp/handle-db-error original-ex)))

    :else
    (let [original-ex (or (:exception (ex-data ex)) ex)]
      (log/error "Unhandled exception:" original-ex)
      (assoc context :response (http-resp/server-error (str original-ex))))))

(defn validate-progress-update []
  (interceptor/interceptor
   {:name ::validate-progress-update
    :enter (fn [context]
             (let [params (get-in context [:request :json-params])]
               (if (s/valid? ::specs/progress-update-params params)
                 context
                 (assoc context :response
                        (http-resp/bad-request 
                         (str "Invalid parameters: " 
                              (s/explain-str ::specs/progress-update-params params)))))))}))