(ns wallstreetbets-crawler.kafka
  (:gen-class)
  (:require
   [clojure.data.json :as json]
   [clojure.edn :as edn]
   [wallstreetbets-crawler.log :as log])
  (:import
   (java.util Properties)
   (org.apache.kafka.clients.admin AdminClient NewTopic)
   (org.apache.kafka.clients.producer Callback KafkaProducer ProducerConfig ProducerRecord)
   (org.apache.kafka.common.errors TopicExistsException)))

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

(defn produce!
  [config topic]
  (with-open [producer (KafkaProducer. (config=>properties config))]
    (doseq [i (range 0 3)]
      (let [callback (reify Callback
                       (onCompletion [_this _record-meta ex]
                         (when ex (log/warn! "Record delivery failure"
                                             {:ex ex}))))]
        (.send
         producer
         (make-record topic i {:count i})
         callback)))
    (.flush producer)))

(produce! (config=>properties kafka-producer-config) "hello_world")
