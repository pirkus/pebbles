(ns pebbles.db
  (:require
   [monger.collection :as mc]
   [monger.operators :refer :all]))

(defn find-progress
  "Find progress document by filename and email"
  [db filename email]
  (mc/find-one-as-map db "progress" {:filename filename :email email}))

(defn find-progress-by-filename
  "Find progress document by filename only (used for authorization checks)"
  [db filename]
  (mc/find-one-as-map db "progress" {:filename filename}))

(defn create-progress
  "Create a new progress document"
  [db progress-data]
  (mc/insert-and-return db "progress" progress-data))

(defn update-progress
  "Update existing progress document with new counts"
  [db filename email update-data]
  (mc/update db "progress" 
             {:filename filename :email email}
             update-data))

(defn find-all-progress
  "Find all progress documents for a user"
  [db email]
  (mc/find-maps db "progress" {:email email}))