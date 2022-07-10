(ns wallstreetbets-crawler
  (:require
   [clj-http.client :as client]
   [clojure.set :refer [rename-keys]]
   [clojure.string :as str]
   [wallstreetbets-crawler.auth :as auth]
   [wallstreetbets-crawler.log :as log]
   [wallstreetbets-crawler.util :as util]))

(def base-url "https://oauth.reddit.com/r/wallstreetbets")

(defn wsb-url
  [& subpaths]
  (str base-url "/" (str/join "/" subpaths)))

(defn truncate
  [s]
  (str (subs s 0 100) "...<truncated>"))

(defn retry-fn-with-wait
  [failed? num-tries nullary-fn]
  (let [og-result (nullary-fn)]
    (loop [remaining-tries (dec num-tries)
           wait-time       1000
           result          og-result]
      (cond
        (<= remaining-tries 0)
        (log/error! "Maximum number of retries reached, returning nil")

        (failed? result)
        (do
          (log/info! (str "Expression failed, retrying after " (/ wait-time 1000) " seconds"))
          (Thread/sleep wait-time)
          (recur (dec remaining-tries)
                 (* 2 wait-time)
                 (nullary-fn)))

        :else result))))

(defmacro with-retries
  "Try evaluationg an expression, and retry if the result failed.
  Returns nil if none of the attempts succeeded.
  "
  [failed? num-tries expr]
  `(retry-fn-with-wait ~failed? ~num-tries (fn [] ~expr)))

(defn log-non-200-error
  [{:keys [url params] :as _request}
   {:keys [status headers body request-time] :as _response}]
  (log/error! "Request returned non-200 status code"
              {:request
               {:url    url
                :params params}
               :reponse
               {:status       status
                :headers      headers
                :request-time request-time
                :body         (truncate body)}}))

(defn refresh-access-token+log-401-error
  [{:keys [url params] :as _request}
   {:keys [status headers body request-time] :as _response}]
  (do
    (log/error! "Request returned 401 status code, refreshing access token ..."
                {:request
                 {:url    url
                  :params params}
                 :response
                 {:status       status,
                  :headers      headers,
                  :request-time request-time
                  :body         (truncate body)}})
    (auth/refresh-access-token!)))

(defn get-with-handlers!
  [url params {:keys [default] :as handlers}]
  (let [params-no-ex (assoc params :throw-exceptions false)
        {:keys [status] :as response} (client/get url params-no-ex)
        request {:url    url
                 :params params-no-ex}
        handler (get handlers status default)]
    (case status
      200 response
      (handler request response))))

(defn get-with-retries!
  [url params]
  (let [{:keys [body]}
        (with-retries #(not= 200 (:status %)) 3
          (get-with-handlers!
           url
           (-> params auth/with-user-agent auth/update-access-token)
           {401      refresh-access-token+log-401-error
            :default log-non-200-error}))]
    (try
      (util/parse-json body)
      (catch Exception e
        (log/error! "Response could not be parsed as JSON"
                    {:request
                     {:url    url
                      :params params}
                     :response
                     {:body body}})))))

(defn get-articles!
  [limit]
  (let [url (wsb-url "new")
        params (->> {:query-params
                     {:limit limit}
                     :accept :json})]
    (get-with-retries! url params)))

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

(defn get-comments!
  [article-id limit]
  (let [url (wsb-url "comments" article-id)
        params (->> {:query-params
                     {:limit limit
                      :depth 10
                      :threaded false}
                     :accept :json})]
    ;; NOTE: the first entry is the article
    (second (get-with-retries! url params))))

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
  []
  (let [articles (get-simplified-articles! 100)]
    (for [{:keys [id]} articles
          comment (:t1 (get-simplified-comments! id 10000))]
      comment)))

(comment

  (get-articles! 1)
  (get-simplified-articles! 5)

  (get-comments! "vttzjg" 1)
  (get-simplified-comments! "vttzjg" 50)

  (count (get-all-comments!))

  "plan of attack
- get top 100 articles
- for each article
  - get all comments
  - for all comments
    - check whether they can be expanded

dedupe state:
- article, comment, childrend
")
