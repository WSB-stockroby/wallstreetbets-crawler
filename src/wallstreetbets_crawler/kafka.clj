(ns wallstreetbets-crawler.kafka
  (:gen-class)
  (:require
   [clojure.data.json :as json]
   [clojure.edn :as edn]
   [wallstreetbets-crawler.log :as log])
  (:import
   (java.util Properties)
   (org.apache.kafka.clients.producer Callback KafkaProducer ProducerRecord)))

;; NOTE: based on https://github.com/confluentinc/examples/blob/a5fbc1920277a8177606b07aa8341d24297084b6/clients/cloud/clojure/src/io/confluent/examples/clients/clj/producer.clj#L1

(defn load-kafka-producer-config!
  []
  (->> (slurp "config.edn")
       (edn/read-string)
       (:kafka)))

(def kafka-producer-config (load-kafka-producer-config!))

(defn config=>properties [config]
  ;; NOTE: https://clojure.org/reference/java_interop
  (doto (Properties.)
    (.putAll config)))

(defn make-record
  [topic k v]
  (ProducerRecord. topic (str k) (json/write-str v)))

(defn record-metadata=>map
  [record-metadata]
  {:topic     (. record-metadata topic)
   :partition (. record-metadata partition)
   :offset    (. record-metadata offset)
   :timestamp (. record-metadata timestamp)})

(defn with-producer
  [config f]
  (with-open [producer (KafkaProducer. (config=>properties config))]
    (f producer)
    (.flush producer)))

(defn send!
  [producer topic k v]
  (let [callback (reify Callback
                   (onCompletion [_this _record-meta ex]
                     (when ex (log/warn! "Record delivery failure"
                                         {:ex ex}))))]
    (.send
     producer
     (make-record topic k v)
     callback)))

(comment

 (with-producer (load-kafka-producer-config!)
   (fn [p]
     (doseq [i (range 0 3)]
       (send! p "whatever" i {:count i}))))

 "asd")
