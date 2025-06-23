(ns pebbles.mock-db
  "Mock database functions for testing without MongoDB")

(defn find-progress
  "Find progress by clientKrn, filename and email"
  [db client-krn filename email]
  (get @db [client-krn filename email]))

(defn find-progress-by-filename
  "Find progress by clientKrn and filename only"
  [db client-krn filename]
  (some (fn [[[c f e] v]]
          (when (and (= c client-krn) (= f filename))
            v))
        @db))

(defn create-progress
  "Create a new progress document"
  [db progress]
  (let [key [(:clientKrn progress) (:filename progress) (:email progress)]
        doc (assoc progress :_id (str (java.util.UUID/randomUUID)))]
    (swap! db assoc key doc)
    doc))

(defn update-progress
  "Update existing progress"
  [db client-krn filename email update-doc]
  (let [key [client-krn filename email]
        existing (get @db key)
        updated (if-let [updates (get update-doc "$set")]
                  (merge existing updates)
                  existing)]
    (swap! db assoc key updated)
    updated))

(defn find-all-progress
  "Find all progress for a user in a client"
  [db client-krn email]
  (->> @db
       (filter (fn [[[c f e] v]]
                 (and (= c client-krn) (= e email))))
       (map second)
       vec))

(defn find-all-progress-by-client
  "Find all progress for a client"
  [db client-krn]
  (->> @db
       (filter (fn [[[c f e] v]]
                 (= c client-krn)))
       (map second)
       vec))