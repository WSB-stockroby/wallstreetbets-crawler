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

(defmacro with-retries
  "Try evaluationg an expression, and retry if the result failed.
  Returns nil if none of the attempts succeeded.
  "
  [failed? num-tries expr]
  (let [wait-times (->> (range 0 (dec num-tries))
                        (map #(Math/pow 2 %))
                        (map (partial * 1000)))
        retries (for [t wait-times]
                  `(do
                     (log/info! ~(str "Expression failed, retrying after " (Math/round (/ t 1000)) " seconds"))
                     (Thread/sleep ~t)
                     ~expr))]
    `(some (complement ~failed?)
           [~expr
            ~@retries
            (log/error! "Maximum number of retries reached, returning nil")])))

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
  (let [{:keys [status] :as response} (client/get url params)
        request {:url url
                 :params params}
        handler (get handlers status default)]
    (handler request response)))

(defn get-with-retries!
  [url params]
  (let [{:keys [body]}
        (with-retries #(not= 200 (:status %)) 3
          (get-with-handlers!
           url params {200 identity
                       401 refresh-access-token+log-401-error
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
  []
  (let [url "https://oauth.reddit.com/r/wallstreetbets/new"
        params (->> {:query-params
                     {:limit 1}
                     :accept :json}
                    auth/with-user-agent
                    auth/with-access-token)]
    (get-with-retries! url params)))

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
