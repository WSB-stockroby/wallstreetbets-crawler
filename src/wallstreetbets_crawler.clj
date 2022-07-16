(ns wallstreetbets-crawler
  (:require
   [wallstreetbets-crawler.crawler :as crawler]
   [wallstreetbets-crawler.kafka :as kafka]))

;; TODO: if kafka is not available for many messages in a row, throw exception and exit
(defn -main
  []
  (let [articles (crawler/get-simplified-articles! 100)
        comments (crawler/get-all-comments! articles)
        kafka-producer-config (kafka/load-kafka-producer-config!)]
    (kafka/with-producer kafka-producer-config
      (fn [p]
        (do
          (doseq [{:keys [fullname] :as comment} comments]
            (kafka/send! p "comments" fullname comment))
          (doseq [{:keys [fullname] :as article} articles]
            (kafka/send! p "articles" fullname article)))))))

(comment
  (-main)
  "asd")
