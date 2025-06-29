(ns pebbles.specs
  (:require [clojure.spec.alpha :as s]))

(s/def ::client-krn (s/and string? #(re-matches #"^krn:clnt:.*" %)))
(s/def ::filename string?)
(s/def ::email (s/and string? #(re-matches #"^[^@]+@[^@]+\.[^@]+$" %)))

(s/def ::done (s/and integer? #(>= % 0)))
(s/def ::warn (s/and integer? #(>= % 0)))
(s/def ::failed (s/and integer? #(>= % 0)))
(s/def ::counts (s/keys :req-un [::done ::warn ::failed]))

;; Error and warning detail specs
(s/def ::line (s/and integer? #(> % 0)))
(s/def ::message string?)

;; Enhanced error detail spec with all fields
(s/def ::pattern string?)
(s/def ::values (s/coll-of string?))

;; Line item spec for pattern-based responses
(s/def ::line-item (s/keys :req-un [::line]
                           :opt-un [::values]))
(s/def ::lines (s/coll-of ::line-item))

;; Separate specs for requests vs responses
;; Request spec - clients send only message and optional line
(s/def ::error-detail-request (s/keys :req-un [::message]
                                      :opt-un [::line]))

;; Response spec - backend returns full details including patterns
(s/def ::error-detail-response (s/keys :req-un [::message]
                                       :opt-un [::line ::lines ::pattern ::values]))

;; Collections for requests and responses
(s/def ::errors (s/coll-of ::error-detail-request))
(s/def ::warnings (s/coll-of ::error-detail-request))
(s/def ::errors-response (s/coll-of ::error-detail-response))
(s/def ::warnings-response (s/coll-of ::error-detail-response))

;; Progress update specs
(s/def ::total (s/nilable (s/and integer? #(> % 0))))
(s/def ::isLast boolean?)

;; Main request spec for progress update - uses request error specs
(s/def ::progress-update-params (s/keys :req-un [::filename ::counts]
                                       :opt-un [::total ::isLast ::errors ::warnings]))

;; Response specs - use response error specs
(s/def ::id string?)
(s/def ::result #{"created" "updated"})
(s/def ::isCompleted boolean?)

;; ISO timestamp predicate
(defn iso-timestamp? [s]
  (and (string? s)
       (re-matches #"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{3})?Z?$" s)))

(s/def ::createdAt iso-timestamp?)
(s/def ::updatedAt iso-timestamp?)

(s/def ::progress-response (s/keys :req-un [::result ::client-krn ::filename ::counts]
                                  :opt-un [::total ::isCompleted ::errors-response ::warnings-response]))

(s/def ::progress-record (s/keys :req-un [::id ::client-krn ::filename ::email ::counts 
                                         ::isCompleted ::createdAt ::updatedAt]
                                :opt-un [::total ::errors-response ::warnings-response]))