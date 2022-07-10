(ns wallstreetbets-crawler
  (:require
   [wallstreetbets-crawler.crawler :as crawler]))

(defn -main
  []
  (doall
   (map prn (crawler/get-all-comments!))))
