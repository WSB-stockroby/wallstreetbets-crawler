(ns wallstreetbets-crawler.log
  (:require
   [wallstreetbets-crawler.util :as util]))

(defn strip-sensitive-data
  [params]
  (dissoc params :headers))

(defn log!
  ([level msg]
   (log! level msg {}))
  ([level msg meta]
   (-> meta
       (util/assoc-front
        :msg msg
        :level level
        :timestamp (util/now))
       (util/update-in-if-exists [:request :params] strip-sensitive-data)
       (prn))))

(defn info!
  ([msg] (log! :INFO msg))
  ([msg meta] (log! :INFO msg meta)))

(defn warn!
  ([msg] (log! :WARN msg))
  ([msg meta] (log! :WARN msg meta)))

(defn error!
  ([msg] (log! :ERROR msg))
  ([msg meta] (log! :ERROR msg meta)))

(defn critical!
  ([msg] (log! :CRITICAL msg))
  ([msg meta] (log! :CRITICAL msg meta)))


(comment

  (info! "asd")
  (info! "asd" {:foo "bar"})

  "asd")
