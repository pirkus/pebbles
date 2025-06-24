(ns pebbles.specs
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]))

;; Client KRN specs
(s/def ::client-krn string?)

;; Common specs
(s/def ::filename string?)
(s/def ::email string?)

;; Count specs
(s/def ::done (s/and integer? #(>= % 0)))
(s/def ::warn (s/and integer? #(>= % 0)))
(s/def ::failed (s/and integer? #(>= % 0)))
(s/def ::counts (s/keys :req-un [::done ::warn ::failed]))

;; Error and warning detail specs
(s/def ::line (s/and integer? #(> % 0)))
(s/def ::message string?)
(s/def ::error-detail (s/keys :req-un [::line ::message]))
(s/def ::warning-detail (s/keys :req-un [::line ::message]))
(s/def ::errors (s/coll-of ::error-detail))
(s/def ::warnings (s/coll-of ::warning-detail))

;; Progress update specs
(s/def ::total (s/nilable (s/and integer? #(> % 0))))
(s/def ::isLast boolean?)

;; Main request spec for progress update - clientKrn is now in path, not body
(s/def ::progress-update-params (s/keys :req-un [::filename ::counts]
                                       :opt-un [::total ::isLast ::errors ::warnings]))

;; Map from camelCase to kebab-case for spec compatibility
(s/def ::clientKrn ::client-krn)