(ns wallstreetbets-crawler
  (:require
   [clj-http.client :as client]
   [slingshot.slingshot :refer [try+]]
   [wallstreetbets-crawler.auth :as auth]
   [wallstreetbets-crawler.log :as log]
   [wallstreetbets-crawler.util :as util]))

(defn truncate
  [s]
  (str (subs s 0 100) "...<truncated>"))

(defn make-request-raw!
  ([url params]
   (make-request-raw! url params 3))
  ([url params remaining-retries]
   (if-not (pos? remaining-retries)
     (log/error! "Number of retries exhausted, skipping request"
                 {:request
                  {:url url
                   :params params}})
     (try+
      (client/get url params)
      (catch [:status 401] {:keys [status headers body request-time]}
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
          (auth/refresh-access-token!)
          (log/info! "Retrying request ..."
                     {:request
                      {:url url
                       :params params}})
          (make-request-raw! url (auth/update-access-token params) (dec remaining-retries))))
      (catch #(not= 200 (:status %)) {:keys [status headers body request-time]}
        (log/error! "Request returned non-200 status code"
                    {:request
                     {:url    url
                      :params params}
                     :reponse
                     {:status       status
                      :headers      headers
                      :request-time request-time
                      :body         (truncate body)}}))))))

(defn make-request!
  [url params]
  (-> (make-request-raw! url params)
      (:body)
      (as-> body
            (try
              (util/parse-json body)
              (catch Exception e
                (log/error! "Response could not be parsed JSON"
                            {:request
                             {:url    url
                              :params params}
                             :response
                             {:body body}}))))))

(defn get-articles!
  []
  (let [url "https://oauth.reddit.com/r/wallstreetbets/new"
        params (->> {:query-params
                     {:limit 1}
                     :accept :json}
                    auth/with-user-agent
                    auth/with-access-token)]
    (make-request! url params)))


(defn extract-article-fields
  [{:keys [data] :as _article}]
  (select-keys
   data
   [:name
    :title
    :selftext
    :created_utc
    :author_fullname
    :link_flair_text
    :ups
    :downs]))

(defn get-simplified-articles!
  []
  (->> (get-articles!)
       (:data)
       (:children)
       (map extract-article-fields)))

(get-simplified-articles!)


(comment

(get-articles!)

  "plan of attack
- get top 100 articles
- for each article
  - get all comments
  - for all comments
    - check whether they can be expanded

dedupe state:
- article, comment, childrend
"


  )
