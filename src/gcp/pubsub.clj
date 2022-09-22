(ns gcp.pubsub
  (:require
   [clojure.data.json :as json]
   [clj-http.client :as client]
   [clojure.algo.generic.functor :refer [fmap]])
  (:import java.util.Base64))

(defn base64-encode [to-encode]
  (.encodeToString (Base64/getEncoder) (.getBytes to-encode)))

(defn base64-decode [to-decode]
  (String. (.decode (Base64/getDecoder) to-decode)))

(def gcp-token "ya29.a0Aa4xrXMj0ImzK8lV26ZAetPe4_T0DUJl8WsmvpehjiPg-hxo3thUAEZFF3C5ZFdBWudxA2NH6cZ_HNzLIJbx05AuqOmLOMEochM7xOMotiJKdqK2sGagNy1YOeJpA3d0hg6PLq_5H-4LXsldNX-bwyqlPRHZ08trr-5maCgYKATASARASFQEjDvL9d9Jd42strJ1UP4gFVuewQA0171")


;; TODO: implement refresh token shit: https://cloud.google.com/identity-platform/docs/use-rest-api

(defn topic->url
  [topic-name]
  (str "https://pubsub.googleapis.com/v1/projects/white-academy-363312/topics/" topic-name ":publish"))

(defn reformat-message
  [data-key message]
  (let [data       (get    message data-key)
        attributes (->> (dissoc message data-key)
                        (fmap str))]
    {:attributes attributes
     :data       (base64-encode data)}))

(defn publish-all!
  [topic data-key messages]
  (let [reformatted-messages (mapv #(reformat-message data-key %) messages)
        url                  (topic->url topic)]
    (client/post
     url
     {:body               (json/write-str {:messages reformatted-messages})
      :headers            {"Authorization" (str "Bearer " gcp-token)}
      :content-type       :json
      :socket-timeout     1000
      :connection-timeout 1000
      :accept             :json})))

(defn batch-publish!
  [batch-size topic data-key messages]
  (doseq [batched-messages (partition batch-size messages)]
    (publish-all! topic data-key batched-messages)))


(comment

  (reformat-message
   "body"
   {"asd"  "qwe"
    "foo"  1
    "body" "bleeeeeeeX"})

  (publish-all!
   "comments"
   "body"
   [{"asd"  "qwe"
     "foo"  1
     "body" "bleeeeeeeX"}])

  (batch-publish!
   2
   "comments"
   "body"
   [{"asd" "qwe"
     "foo" "bar"
     "body" "bleeeeeee1"}

    {"asd" "qwe"
     "foo" "bar"
     "body" "bleeeeeee2"}

    {"asd" "qwe"
     "foo" "bar"
     "body" "bleeeeeee3"}

    {"asd" "qwe"
     "foo" "bar"
     "body" "bleeeeeee4"}

    {"asd" "qwe"
     "foo" "bar"
     "body" "bleeeeeee5"}

    {"asd" "qwe"
     "foo" "bar"
     "body" "bleeeeeee6"}])

  "asd")


