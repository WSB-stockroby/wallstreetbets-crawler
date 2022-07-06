(ns wallstreetbets-crawler
  (:require
   [clj-http.client :as client]
   [clojure.data.json :as json]))

(def wsb-url "https://www.reddit.com/r/wallstreetbets/.json")

(defn parse-json
  [raw-json-str]
  (json/read-str raw-json-str :key-fn keyword))

(->> (client/get wsb-url)
     :body
    parse-json)

(->> "https://www.reddit.com/api/v1/me"
     client/get
     :body
     parse-json)
