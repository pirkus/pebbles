(ns pebbles.spec-openapi
  (:require [clojure.spec.alpha :as s]
            [pebbles.specs]))

;; ----------------------------------------------------------------------------
;; Spec to OpenAPI Schema Conversion
;; ----------------------------------------------------------------------------

(defmulti spec->openapi
  "Convert a Clojure spec to an OpenAPI schema"
  (fn [spec-form]
    (cond
      (keyword? spec-form) :keyword
      (symbol? spec-form) :symbol
      (seq? spec-form) (first spec-form)
      (fn? spec-form) :predicate
      :else :unknown)))

(defmethod spec->openapi :keyword
  [spec-kw]
  (let [;; For unqualified keywords in a keys spec, they get resolved in the spec namespace
        resolved-kw (if (qualified-keyword? spec-kw)
                     spec-kw
                     (keyword "pebbles.specs" (name spec-kw)))]
    (if-let [spec-form (s/form resolved-kw)]
      (spec->openapi spec-form)
      ;; Handle base types
      (case spec-kw
        :string {:type "string"}
        :int {:type "integer"}
        :boolean {:type "boolean"}
        :any {:type "object"}
        ;; If we can't resolve it, assume it's a reference
        {:$ref (str "#/components/schemas/" (name spec-kw))}))))

(defmethod spec->openapi 'clojure.spec.alpha/and
  [[_ & preds]]
  (let [schemas (map spec->openapi preds)
        ;; Find the base type from the predicates
        base-schema (or (first (filter #(contains? % :type) schemas))
                        {})
        ;; Extract constraints - filter out schemas that only have :type
        constraints (apply merge (filter #(or (contains? % :minimum)
                                            (contains? % :maximum)
                                            (contains? % :pattern)
                                            (contains? % :format)) 
                                       schemas))
        ;; Check if this is an email pattern
        email-pattern? (and (:pattern constraints)
                           (or (= (:pattern constraints) "^[^@]+@[^@]+\\.[^@]+$")
                               (re-matches #".*@.*" (:pattern constraints))))]
    (if email-pattern?
      (-> base-schema
          (merge (dissoc constraints :pattern))
          (assoc :format "email"))
      (merge base-schema constraints))))

(defmethod spec->openapi 'clojure.spec.alpha/or
  [[_ & name-pred-pairs]]
  {:oneOf (mapv (fn [[_ pred]] (spec->openapi pred))
                (partition 2 name-pred-pairs))})

(defmethod spec->openapi 'clojure.spec.alpha/nilable
  [[_ spec]]
  (let [base-schema (spec->openapi spec)]
    {:oneOf [base-schema {:type "null"}]}))

(defmethod spec->openapi 'clojure.spec.alpha/keys
  [[_ & {:keys [req req-un opt opt-un]}]]
  (let [required-keys (vec (concat (map name req) 
                                  (map #(-> % name keyword name) req-un)))
        all-keys (concat req req-un opt opt-un)
        properties (reduce (fn [props k]
                            (let [key-name (if (qualified-keyword? k)
                                            (name k)
                                            (-> k name keyword name))]
                              (assoc props key-name (spec->openapi k))))
                          {}
                          all-keys)]
    (cond-> {:type "object"
             :properties properties}
      (seq required-keys) (assoc :required required-keys))))

(defmethod spec->openapi 'clojure.spec.alpha/coll-of
  [[_ item-spec]]
  (let [item-schema (spec->openapi item-spec)]
    ;; If the item is a complex object, create a reference
    (if (and (map? item-schema) 
             (= "object" (:type item-schema))
             (not (contains? item-schema :$ref)))
      {:type "array"
       :items {:$ref (str "#/components/schemas/" 
                         (name item-spec))}}
      {:type "array"
       :items item-schema})))

(defmethod spec->openapi :symbol
  [sym]
  (let [sym-name (name sym)
        sym-ns (namespace sym)]
    (cond
      ;; Check for timestamp predicates
      (or (= sym-name "iso-timestamp?")
          (and (= sym-ns "pebbles.specs") (= sym-name "iso-timestamp?")))
      {:type "string" :format "date-time"}
      
      (or (= sym 'string?) (= sym 'clojure.core/string?) (= sym-name "string?")) 
      {:type "string"}
      
      (or (= sym 'integer?) (= sym 'clojure.core/integer?) (= sym-name "integer?")) 
      {:type "integer"}
      
      (or (= sym 'int?) (= sym 'clojure.core/int?) (= sym-name "int?")) 
      {:type "integer"}
      
      (or (= sym 'boolean?) (= sym 'clojure.core/boolean?) (= sym-name "boolean?")) 
      {:type "boolean"}
      
      (or (= sym 'number?) (= sym 'clojure.core/number?) (= sym-name "number?")) 
      {:type "number"}
      
      (or (= sym 'pos-int?) (= sym 'clojure.core/pos-int?) (= sym-name "pos-int?")) 
      {:type "integer" :minimum 1}
      
      (or (= sym 'nat-int?) (= sym 'clojure.core/nat-int?) (= sym-name "nat-int?")) 
      {:type "integer" :minimum 0}
      
      :else {})))

(defmethod spec->openapi :predicate
  [pred]
  (cond
    ;; Handle set predicates (enums)
    (set? pred) {:type "string" :enum (vec pred)}
    
    ;; Handle symbols
    :else
    (let [pred-name (if (symbol? pred) pred (first pred))]
      (cond
        (= pred-name 'string?) {:type "string"}
        (= pred-name 'integer?) {:type "integer"}
        (= pred-name 'int?) {:type "integer"}
        (= pred-name 'boolean?) {:type "boolean"}
        (= pred-name 'number?) {:type "number"}
        (= pred-name 'pos-int?) {:type "integer" :minimum 1}
        (= pred-name 'nat-int?) {:type "integer" :minimum 0}
        :else {}))))

(defmethod spec->openapi 'fn
  [[_ _ body]]
  ;; Handle anonymous functions like #(>= % 0)
  (cond
    ;; Check for regex patterns - e.g. (re-matches #"pattern" %)
    (and (seq? body)
         (or (= 're-matches (first body))
             (= 'clojure.core/re-matches (first body)))
         (= 3 (count body))
         (and (seq? (second body)) 
              (= 're-pattern (first (second body)))))
    (let [pattern-str (second (second body))]
      {:pattern pattern-str})
    
    ;; Check for simple regex patterns with literal regex
    (and (seq? body)
         (or (= 're-matches (first body))
             (= 'clojure.core/re-matches (first body)))
         (= 3 (count body))
         (instance? java.util.regex.Pattern (second body)))
    {:pattern (.pattern (second body))}
    
    ;; Check for >= patterns - e.g. (>= % 0) or (>= x 0)
    (and (seq? body) 
         (or (= '>= (first body)) 
             (= 'clojure.core/>= (first body)))
         (= 3 (count body)))
    (let [min-val (last body)]
      {:minimum min-val})
    
    ;; Check for > patterns - e.g. (> % 0) or (> x 0)  
    (and (seq? body) 
         (or (= '> (first body))
             (= 'clojure.core/> (first body)))
         (= 3 (count body)))
    (let [min-val (last body)]
      {:minimum (inc min-val)})
    
    ;; Check for < patterns
    (and (seq? body) 
         (or (= '< (first body))
             (= 'clojure.core/< (first body)))
         (= 3 (count body)))
    (let [max-val (last body)]
      {:maximum max-val})
    
    ;; Check for <= patterns
    (and (seq? body) 
         (or (= '<= (first body))
             (= 'clojure.core/<= (first body)))
         (= 3 (count body)))
    (let [max-val (last body)]
      {:maximum max-val})
    
    :else {}))

(defmethod spec->openapi 'clojure.core/fn
  [[_ args body]]
  ;; Delegate to the regular fn handler
  (spec->openapi (list 'fn args body)))

(defmethod spec->openapi :default
  [_]
  {})

;; ----------------------------------------------------------------------------
;; Generate OpenAPI schemas from registered specs
;; ----------------------------------------------------------------------------

(defn generate-schemas-from-specs
  "Generate OpenAPI schemas from registered Clojure specs"
  [spec-keywords]
  (reduce (fn [schemas spec-kw]
            (let [schema-name (name spec-kw)
                  spec-form (s/form spec-kw)]
              (if spec-form
                (assoc schemas schema-name (spec->openapi spec-form))
                schemas)))
          {}
          spec-keywords))

;; ----------------------------------------------------------------------------
;; Integration with existing OpenAPI generation
;; ----------------------------------------------------------------------------

(defn specs->openapi-schemas
  "Convert Pebbles specs to OpenAPI schemas"
  []
  (let [;; Define which specs to convert
        specs-to-convert [:pebbles.specs/counts
                         :pebbles.specs/error-detail-full
                         :pebbles.specs/progress-update-params
                         :pebbles.specs/progress-response
                         :pebbles.specs/progress-record]]
    ;; Just return the generated schemas without any PascalCase mappings
    (generate-schemas-from-specs specs-to-convert)))