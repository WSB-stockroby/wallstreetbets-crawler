(ns wallstreetbets-crawler.util
  (:require
   [clojure.data.json :as json]
   [java-time :as java-time])
  (:import (java.time.format DateTimeFormatterBuilder)))

(defn parse-json
  [raw-json-str]
  (some-> raw-json-str
          (json/read-str :key-fn keyword)))

(defn update-in-if-exists
  [m ks f]
  (cond-> m
    (not= ::default (get-in m ks ::default)) (update-in ks f)))

;; NOTE: If the resulting map contains at most 8 key-value pairs,
;; it will assoc the new kvs to the front of the map.
;; This works, because hash-maps are represented as kv vectors for small elem counts (<=8).
(defn assoc-front
  [m & kvs]
  (let [kvs-map (into {} (map vec (partition 2 kvs)))]
    (into kvs-map m)))

(def iso-instant-ms-formatter
  (-> (DateTimeFormatterBuilder.) (.appendInstant 3) .toFormatter))

(defn seconds->instant
  [seconds]
  (some->> seconds
           (* 1000)
           (java-time/instant)))

(defn instant->timestamp
  [instant]
  (some->> instant
           (java-time/format iso-instant-ms-formatter)))

(defn now []
  (->> (java-time/instant)
       (instant->timestamp)))

(defn seconds->timestamp
  [seconds]
  (->> seconds
       seconds->instant
       instant->timestamp))

(comment

  (java-time/instant (* 1000 1657401968.0))
  (seconds->instant 1657401968.0)
  (seconds->instant nil)
  (instant->timestamp nil)

  (seconds->timestamp 1657401968.0)
  (update-in-if-exists {:a {:b "x"}} [:a :b] keyword)
  (update-in-if-exists {:a {:c "x"}} [:a :b] keyword)
  (update-in-if-exists {:a "x"} [:a :b] keyword)

  (assoc-front {:a "123"}
               :x "1"
               :y "2"
               :z "3"
               :z2 "4"
               :z3 "5"
               :z4 "5"
               :z10 "5")

  (assoc {:a "123"}
         :x "1"
         :y "2"
         :z "3")

  "asd")
