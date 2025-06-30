(ns pebbles.statistical-grouping
  (:require
   [clojure.string :as str]))

;; ----------------------------------------------------------------------------
;; Regex patterns for token recognition
;; ----------------------------------------------------------------------------

(def token-patterns
  "Regular expression patterns for identifying different types of tokens"
  {:quoted #"(?:'[^']*'|\"[^\"]*\")"
   :number-basic #"^\d+[A-Za-z]*$"              ; For is-likely-variable? 
   :number-with-punct #"^\d+[A-Za-z]*[)\]]*$"   ; For normalize-token (allows trailing punctuation)
   :number-formatted #"^[\d,.\-]+$"
   :currency #"^\$[\d,.\-]+$"
   :percentage #"^\d+%$"
   :duration #"^\d+[smhd]$"
   :alphanumeric-id #"^[a-zA-Z0-9\-_]+$"
   :email #"[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+[a-zA-Z]"  ; More flexible email pattern
   :file-path #"^[/\\].*[/\\].*"
   :filename #"^[^/\\]+\.[a-zA-Z]{2,4}$"
   :parenthetical #"^\(.*:.*\)$"})

(defn extract-quoted-strings
  "Extract all quoted strings from a message"
  [message]
  (re-seq (:quoted token-patterns) message))

(defn replace-quotes-with-placeholders
  "Replace quoted strings with placeholders in the message"
  [message quoted-strings]
  (reduce (fn [m [idx q]]
            (str/replace m q (str "QUOTE" idx)))
          message
          (map-indexed vector quoted-strings)))

(defn restore-quoted-strings
  "Restore quoted strings from placeholders"
  [token quoted-strings]
  (if-let [[_ idx] (re-find #"QUOTE(\d+)" token)]
    (nth quoted-strings (Integer/parseInt idx))
    token))

(defn tokenize-message
  "Split a message into meaningful tokens preserving common patterns"
  [message]
  (if (nil? message)
    []
    ;; Split on whitespace but preserve quoted strings and common patterns
    (let [quoted-strings (extract-quoted-strings message)
          msg-without-quotes (replace-quotes-with-placeholders message quoted-strings)
          tokens (str/split msg-without-quotes #"\s+")
          restored-tokens (map #(restore-quoted-strings % quoted-strings) tokens)]
      (vec restored-tokens))))

;; positions with high variability are more likely to be variables
;; input is a list of tokenized messages: [[token1 token2 token5] [token1 token2 token6] [token1 token4 token7]]
;; output is a list of variability scores for each position: [0.0 0.5 1.0]
(defn calculate-token-variability
  "Calculate variability score for each token position"
  [tokenized-messages]
  (when (empty? tokenized-messages)
    [])
  (let [max-length (apply max 0 (map count tokenized-messages))]
    (vec
     (for [i (range max-length)]
       (let [tokens-at-pos (keep #(get % i) tokenized-messages)
             unique-count (count (distinct tokens-at-pos))
             total-count (count tokens-at-pos)]
         (if (or (zero? total-count) (= 1 total-count))
           0.0
           ;; 0.0 if all same, 1.0 if all different
           (double (/ (dec unique-count) (dec total-count)))))))))

(defn is-likely-variable?
  "Heuristic to determine if a token is likely variable data"
  [token]
  (and (string? token)
       (or
        ;; Pure numbers or numbers with units
        (re-matches (:number-basic token-patterns) token)
        ;; Numbers with formatting
        (re-matches (:number-formatted token-patterns) token)
        ;; Currency amounts
        (re-matches (:currency token-patterns) token)
        ;; Percentages
        (re-matches (:percentage token-patterns) token)
        ;; Time durations
        (re-matches (:duration token-patterns) token)
        ;; Alphanumeric IDs (must have both letters and numbers)
        (and (re-find #"\d" token)
             (re-find #"[a-zA-Z]" token)
             (re-matches (:alphanumeric-id token-patterns) token))
        ;; Email addresses
        (re-find (:email token-patterns) token)
        ;; File paths
        (re-matches (:file-path token-patterns) token)
        ;; File names with extensions
        (re-matches (:filename token-patterns) token)
        ;; Parenthetical info
        (re-matches (:parenthetical token-patterns) token))))

(defn normalize-token
  "Normalize a single token based on its characteristics"
  [token]
  (cond
    ;; Keep quoted strings but mark them as variables
    (or (and (str/starts-with? token "'") (str/ends-with? token "'"))
        (and (str/starts-with? token "\"") (str/ends-with? token "\"")))
    "{QUOTED}"
    
    ;; Numbers (including with units like MB, GB, etc.) - also handle trailing punctuation
    (re-matches (:number-with-punct token-patterns) token)
    "{NUMBER}"
    
    ;; Currency
    (re-matches (:currency token-patterns) token)
    "{AMOUNT}"
    
    ;; Percentages
    (re-matches (:percentage token-patterns) token)
    "{PERCENT}"
    
    ;; Email
    (re-find (:email token-patterns) token)
    "{EMAIL}"
    
    ;; File paths
    (re-matches (:file-path token-patterns) token)
    "{PATH}"
    
    ;; File names
    (re-matches (:filename token-patterns) token)
    "{FILENAME}"
    
    ;; Parenthetical content with colon (like "(size: 15MB)")
    (re-matches (:parenthetical token-patterns) token)
    "{INFO}"
    
    ;; Time durations (30s, 5m, 2h, etc.)
    (re-matches (:duration token-patterns) token)
    "{DURATION}"
    
    ;; Default - keep as is
    :else token))

(defn message-similarity
  "Calculate similarity between two messages - length-tolerant for stack traces"
  [msg1 msg2]
  (let [tokens1 (tokenize-message msg1)
        tokens2 (tokenize-message msg2)
        len1 (count tokens1)
        len2 (count tokens2)]
    (if (and (zero? len1) (zero? len2))
      1.0  ; Both empty
      (if (or (zero? len1) (zero? len2))
        0.0  ; One empty, one not
        ;; Calculate similarity based on the overlapping portion
        (let [common-len (min len1 len2)
              max-len (max len1 len2)
              ;; Count matches in the overlapping portion
              matches (count (filter identity
                                    (map (fn [t1 t2]
                                           (or (= t1 t2)
                                               (and (is-likely-variable? t1)
                                                    (is-likely-variable? t2))
                                               ;; Also match if they normalize to same placeholder
                                               (= (normalize-token t1) 
                                                  (normalize-token t2))))
                                         (take common-len tokens1)
                                         (take common-len tokens2))))]
          ;; Return similarity as ratio of matches to longest string length
          ;; This gives more realistic scores: 12 matches out of 14 tokens = 0.857
          (double (/ matches max-len)))))))

(defn find-similar-group
  "Find a group that contains a message similar to the given message"
  [groups msg threshold]
  (first
   (filter
    (fn [group]
      (some #(>= (message-similarity (:message msg) 
                                   (:message %)) 
                threshold)
            (:members group)))
    groups)))

(defn group-messages-by-similarity
  "Group messages based on similarity threshold"
  [messages threshold]
  (reduce
   (fn [acc msg]
     (if-let [found-group (find-similar-group acc msg threshold)]
       (map (fn [g]
              (if (= g found-group)
                (update g :members conj msg)
                g))
            acc)
       (conj acc {:members [msg]})))
   []
   messages))

(defn extract-pattern-from-group
  "Extract pattern and variations from a group of similar messages"
  [group]
  (let [messages (map :message (:members group))
        tokenized (map tokenize-message messages)
        ;; Find the pattern by normalizing tokens
        pattern-tokens (if (= 1 (count tokenized))
                        ;; Single message - normalize it
                        (map normalize-token (first tokenized))
                        ;; Multiple messages - find common pattern
                        (let [variability (calculate-token-variability tokenized)]
                          (map-indexed
                           (fn [i tokens-at-pos]
                             (let [var-score (get variability i 0)]
                               (if (> var-score 0.5)
                                 (let [normalized (normalize-token (first tokens-at-pos))]
                                   ;; If variability is high but token wasn't normalized, use generic placeholder
                                   (if (= normalized (first tokens-at-pos))
                                     "{VARIABLE}"
                                     normalized))
                                 (first tokens-at-pos))))
                           (apply map vector tokenized))))
        pattern (str/join " " pattern-tokens)
        ;; Extract variations for variable positions - keep line mapping
        var-positions (keep-indexed
                      (fn [i token]
                        (when (str/starts-with? (str token) "{")
                          i))
                      pattern-tokens)
        ;; Create line items with their variations
        line-items (if (empty? var-positions)
                    ;; No variations - just lines
                    (map (fn [member]
                          {:line (:line member)})
                         (:members group))
                    ;; With variations - include extracted values for each line
                    (map (fn [member tokens]
                          {:line (:line member)
                           :values (vec (map #(get tokens %) var-positions))})
                         (:members group)
                         tokenized))]
    {:pattern pattern
     :lines line-items}))

(defn group-similar-messages
  "Group messages by similarity"
  ([messages] (group-similar-messages messages {}))
  ([messages {:keys [threshold] :or {threshold 0.7}}]
   (let [groups (group-messages-by-similarity messages threshold)]
     (map extract-pattern-from-group groups))))

(defn consolidate-messages-with-patterns
  "Main entry point for message consolidation"
  [items]
  (group-similar-messages items))

(defn pattern-matches-message?
  "Check if a message matches a given pattern"
  [pattern message]
  (let [pattern-tokens (tokenize-message pattern)
        message-tokens (tokenize-message message)]
    (and (= (count pattern-tokens) (count message-tokens))
         (every? identity
                 (map (fn [pt mt]
                        (or (= pt mt)
                            (and (str/starts-with? pt "{")
                                 (str/ends-with? pt "}")
                                 (or (= pt (normalize-token mt))
                                     ;; {VARIABLE} is a catch-all that matches any token that doesn't match specific patterns
                                     (and (= pt "{VARIABLE}")
                                          (= (normalize-token mt) mt))))))
                      pattern-tokens
                      message-tokens)))))

(defn extract-values-for-pattern
  "Extract values from a message that match the variable positions in a pattern"
  [pattern message]
  (let [pattern-tokens (tokenize-message pattern)
        message-tokens (tokenize-message message)
        var-positions (keep-indexed
                      (fn [i token]
                        (when (and (string? token)
                                  (str/starts-with? token "{")
                                  (str/ends-with? token "}"))
                          i))
                      pattern-tokens)]
    (vec (map #(get message-tokens %) var-positions))))

(defn match-against-patterns
  "Try to match a message against existing patterns, return the matching pattern or nil"
  [message existing-patterns]
  (first (filter #(pattern-matches-message? % message) existing-patterns)))

(defn match-items-against-patterns
  "Partition items into matched (with pattern) and unmatched"
  [items existing-patterns]
  (reduce (fn [acc item]
            (if-let [pattern (match-against-patterns (:message item) existing-patterns)]
              (update acc :matched conj (assoc item :matched-pattern pattern))
              (update acc :unmatched conj item)))
          {:matched [] :unmatched []}
          items))

(defn group-matched-by-pattern
  "Group matched items by their pattern and extract values"
  [matched-items]
  (when (seq matched-items)
    (->> matched-items
         (group-by :matched-pattern)
         (map (fn [[pattern items]]
                {:pattern pattern
                 :lines (map (fn [item]
                              {:line (:line item)
                               :values (extract-values-for-pattern pattern (:message item))})
                            items)
})))))

(defn merge-groups-with-existing
  "Merge new groups with existing groups, combining line numbers and counts"
  [existing-groups new-groups]
  (let [all-patterns (into {} (map (fn [g] [(:pattern g) g]) existing-groups))]
    (vals (reduce (fn [acc new-group]
                   (update acc (:pattern new-group)
                          (fn [existing]
                            (if existing
                              {:pattern (:pattern existing)
                               :lines (concat (:lines existing) (:lines new-group))}
                              new-group))))
                 all-patterns
                 new-groups))))

(defn consolidate-with-existing-patterns
  "Consolidate messages using existing patterns when possible"
  [items existing-groups]
  (let [existing-patterns (map :pattern existing-groups)
        {:keys [matched unmatched]} (match-items-against-patterns items existing-patterns) 
        matched-groups (group-matched-by-pattern matched) 
        new-groups (when (seq unmatched)
                    (consolidate-messages-with-patterns unmatched))]
    (concat
     (merge-groups-with-existing existing-groups matched-groups)
     (or new-groups []))))

