(ns wallstreetbets-crawler.crawler
  (:require
   [clojure.set :refer [rename-keys]]
   [wallstreetbets-crawler.client :as client]
   [wallstreetbets-crawler.log :as log]
   [wallstreetbets-crawler.util :as util]))

(defn get-articles!
  [limit]
  (let [url (client/wsb-url "new")
        params (->> {:query-params
                     {:limit limit}
                     :accept :json})]
    (client/get-with-retries! url params)))

(defn fullname->id
  [fullname]
  (second (re-matches #"t\d_(\w+)" fullname)))

(defn extract-article-fields
  [{:keys [data] :as _article}]
  (-> data
      (select-keys
       [:name
        :title
        :selftext
        :created_utc
        :author_fullname
        :link_flair_text
        :ups
        :downs])
      (rename-keys {:name            :fullname
                    :created_utc     :created_at
                    :link_flair_text :flair})
      (update :created_at util/seconds->instant)
      (#(assoc % :id (fullname->id (:fullname %))))))

(defn get-simplified-articles!
  [limit]
  (->> (get-articles! limit)
       (:data)
       (:children)
       (map extract-article-fields)))

#_(defn get-more-children!
  [article-fullname children-ids]
  (let [url (wsb-url "api" "morechildren")
        params (->> {:query-params
                     {:link_id  article-fullname
                      :children children-ids
                      :api-type "json"}
                     :accept :json})]
    (get-with-retries! url params)))

(defn get-comments!
  [article-id limit]
  (let [url (client/wsb-url "comments" article-id)
        params (->> {:query-params
                     {:limit limit
                      :depth 10
                      :threaded false}
                     :accept :json})]
    ;; NOTE: the first entry is the article
    (second (client/get-with-retries! url params))))

(defn partition-comments
  [comments]
  (-> (group-by :kind comments)
      (rename-keys {"t1"   :t1
                    "more" :more})))

(defn extract-more-comments-fields
  [{:keys [data] :as _more-comment}]
  (-> data
      (select-keys [:name
                    :id
                    :parent_id
                    :children])
      (rename-keys {:name :fullname})))

(defn extract-comment-fields
  [{:keys [data] :as _comment}]
  (-> data
      (select-keys
       [:name
        :id
        :body
        :created_utc
        :author_fullname
        :parent_id
        :link_id
        :ups
        :downs])
      (rename-keys {:name        :fullname
                    :created_utc :created_at})
      (update :created_at util/seconds->instant)))

;; NOTE: currenty, we do not follow "more comments",
;; but if the limit is high enough, those comments will be expanded anyway
(defn get-simplified-comments!
  [article-id limit]
  (let [{:keys [more] :as comments}
        (-> (get-comments! article-id limit)
            (:data)
            (:children)
            (partition-comments)
            (update :t1   #(map extract-comment-fields %))
            (update :more #(map extract-more-comments-fields %)))

        num-more (count more)]
    (do
      (when (pos? num-more)
        (log/info! "Skipping following more comments"
                   {:count      (count more)
                    :article_id article-id}))
      comments)))

(defn get-all-comments!
  ([]
   (get-all-comments! (get-simplified-articles! 100)))
  ([articles]
   (for [{:keys [id]} articles
         comment (:t1 (get-simplified-comments! id 10000))]
     comment)))

(comment

  (get-articles! 1)
  (get-simplified-articles! 5)

  (get-comments! "vttzjg" 1)
  (get-simplified-comments! "vttzjg" 50)

  (count (get-all-comments!))

  " ")
