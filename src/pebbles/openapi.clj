(ns pebbles.openapi
  (:require
   [clojure.string :as str]
   [io.pedestal.interceptor :as interceptor]
   [pebbles.spec-openapi :as spec-openapi]))

;; ----------------------------------------------------------------------------
;; OpenAPI Schema Helpers
;; ----------------------------------------------------------------------------

(defn ref-schema [schema-name]
  {:$ref (str "#/components/schemas/" schema-name)})

(def common-schemas
  ;; Generate schemas from specs
  (spec-openapi/specs->openapi-schemas))

(def common-parameters
  {:clientKrn
   {:name "clientKrn"
    :in "path"
    :required true
    :schema (spec-openapi/spec->openapi :pebbles.specs/client-krn)
    :description "Client KRN identifier"}
   
   :filename
   {:name "filename"
    :in "query"
    :required false
    :schema (spec-openapi/spec->openapi :pebbles.specs/filename)
    :description "Filter by specific filename"}
   
   :email
   {:name "email"
    :in "query"
    :required false
    :schema (spec-openapi/spec->openapi :pebbles.specs/email)
    :description "Filter by user email"}})

(def common-responses
  {:400 {:description "Bad request"}
   :401 {:description "Unauthorized"}
   :403 {:description "Forbidden"}
   :404 {:description "Not found"}})

;; ----------------------------------------------------------------------------
;; Metadata extractors
;; ----------------------------------------------------------------------------

(defn extract-openapi-metadata
  "Extracts OpenAPI metadata from a handler or interceptor"
  [handler]
  (cond
    ;; Direct metadata on a function
    (and (fn? handler) (meta handler))
    (meta handler)
    
    ;; Pedestal interceptor wrapper - check the original handler
    (satisfies? io.pedestal.interceptor/IntoInterceptor handler)
    (try
      (let [interceptor-map (io.pedestal.interceptor/-interceptor handler)]
        (or 
         ;; Check if the interceptor has openapi metadata
         (:openapi interceptor-map)
         ;; Check if the enter function has metadata (wrapped handler)
         (when-let [enter-fn (:enter interceptor-map)]
           (meta enter-fn))
         ;; Try to extract from the name/handler reference
         (when-let [handler-name (:name interceptor-map)]
           (cond
             ;; If it's a var, get metadata from the var's value
             (var? handler-name) (meta @handler-name)
             ;; If it's a function, get its metadata
             (fn? handler-name) (meta handler-name)
             ;; Try to resolve it as a symbol and extract metadata
             (symbol? handler-name)
             (when-let [resolved (try (resolve handler-name) (catch Exception _ nil))]
               (meta @resolved))
             :else nil))))
      (catch Exception _ nil))
    
    ;; Handler factory function that returns a handler with metadata
    ;; This handles cases like (handlers/update-progress-handler db)
    (fn? handler)
    (try 
      (let [test-handler (handler nil)]
        (when (and (fn? test-handler) (meta test-handler))
          (meta test-handler)))
      (catch Exception _ nil))
    
    ;; Interceptor map with metadata
    (and (map? handler) (:openapi handler))
    (:openapi handler)
    
    ;; Interceptor with handler function that has metadata
    (and (map? handler) (:enter handler) (meta (:enter handler)))
    (meta (:enter handler))
    
    :else nil))

(defn routes->openapi-paths
  "Converts Pedestal routes to OpenAPI paths"
  [routes]
  (reduce
    (fn [paths route]
      (let [{:keys [path method interceptors path-params]} route
            ;; Find handler (usually last interceptor)
            handler (last interceptors)
            openapi-meta (extract-openapi-metadata handler)]
        (if openapi-meta
          (let [;; Convert Pedestal path to OpenAPI format
                openapi-path (str/replace path #":([^/]+)" "{$1}")
                ;; Extract method-specific metadata
                method-meta (get openapi-meta method)
                ;; Add path parameters from route definition
                path-params-spec (when path-params
                                  (map (fn [[param-name _]]
                                         (or (get common-parameters param-name)
                                             {:name (name param-name)
                                              :in "path"
                                              :required true
                                              :schema {:type "string"}}))
                                       path-params))
                ;; Merge with existing parameters
                all-params (concat (or (:parameters method-meta) [])
                                 path-params-spec)]
            (assoc-in paths [openapi-path method]
                     (-> method-meta
                         (assoc :parameters all-params)
                         (update :responses #(merge common-responses %)))))
          paths)))
    {}
    routes))

(defn generate-openapi-spec
  "Generates OpenAPI spec from routes with metadata"
  [routes & {:keys [info servers] 
             :or {info {:title "API"
                       :version "1.0.0"}
                  servers [{:url "http://localhost:8081"}]}}]
  {:openapi "3.0.0"
   :info info
   :servers servers
   :paths (routes->openapi-paths routes)
   :components {:securitySchemes {:bearerAuth {:type "http"
                                               :scheme "bearer"
                                               :bearerFormat "JWT"}}
                :schemas common-schemas}})

;; ----------------------------------------------------------------------------
;; Handler decorator
;; ----------------------------------------------------------------------------

(defn with-openapi
  "Decorates a handler with OpenAPI metadata"
  [handler openapi-spec]
  (if (fn? handler)
    (with-meta handler openapi-spec)
    (assoc handler :openapi openapi-spec)))