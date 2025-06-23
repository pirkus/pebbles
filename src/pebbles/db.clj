(ns pebbles.db
  (:require
   [monger.collection :as mc]))

(defn find-progress
  "Find progress document by clientKrn, filename and email"
  [db client-krn filename email]
  (mc/find-one-as-map db "progress" {:clientKrn client-krn :filename filename :email email}))

(defn find-progress-by-filename
  "Find progress document by clientKrn and filename only (used for authorization checks)"
  [db client-krn filename]
  (mc/find-one-as-map db "progress" {:clientKrn client-krn :filename filename}))

(defn create-progress
  "Create a new progress document"
  [db progress-data]
  (mc/insert-and-return db "progress" progress-data))

(defn update-progress
  "Update existing progress document with new counts"
  [db client-krn filename email update-data]
  (mc/update db "progress" 
             {:clientKrn client-krn :filename filename :email email}
             update-data))

(defn find-all-progress
  "Find all progress documents for a user in a specific client"
  [db client-krn email]
  (mc/find-maps db "progress" {:clientKrn client-krn :email email}))

(defn find-all-progress-by-client
  "Find all progress documents for a specific client"
  [db client-krn]
  (mc/find-maps db "progress" {:clientKrn client-krn}))