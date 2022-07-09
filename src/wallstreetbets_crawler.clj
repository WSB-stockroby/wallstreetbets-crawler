(ns wallstreetbets-crawler
  (:require
   [clj-http.client :as client]
   [clojure.data.json :as json]
   [clojure.edn :as edn]
   [clojure.string :as str]
   [slingshot.slingshot :refer [throw+ try+]]))

(defn parse-json
  [raw-json-str]
  (some-> raw-json-str
          (json/read-str :key-fn keyword)))

(defn strip-sensitive-data
  [params]
  (dissoc params :headers))

(defn log!
  [msg]
  (->> (if (map? msg)
         (update-in msg [:request :params] strip-sensitive-data)
         msg)
       prn))

(def user-agent "wallstreetbets-crawler-AnabraPP")
(defn with-user-agent
  [request]
  (update request :headers #(assoc % "User-Agent" user-agent)))

(def secrets
  (->> (slurp "secret.edn")
       (edn/read-string)))

;; TODO: add error-handling, retry mechanism
(defn get-access-token!
  []
  (let [{:keys [client-id client-secret refresh-token]} secrets]
    (->> (client/post "https://www.reddit.com/api/v1/access_token"
                      (with-user-agent
                        {:basic-auth [client-id client-secret]
                         :form-params
                         {:grant_type "refresh_token"
                          :refresh_token refresh-token}}))
         (:body)
         (parse-json)
         (:access_token))))

(def access-token
  (atom (get-access-token!)))

(defn with-access-token
  [request]
  (update request :headers #(assoc % "Authorization" (str "Bearer " @access-token))))
(def update-access-token with-access-token)

(defn refresh-access-token!
  []
  (do
    (reset! access-token (get-access-token!))
    (log! "Access token refreshed")))

(defn make-request-raw!
  ([url params]
   (make-request-raw! url params 3))
  ([url params remaining-retries]
   (if-not (pos? remaining-retries)
     (log! {:msg "Number of retries exhausted, skipping request"
            :url url
            :params params})
     (try+
      (client/get url params)
      (catch [:status 401] {:keys [status headers body request-time]}
        (do
          (log! {:msg "Request returned 401 status code, refreshing access token ..."
                 :request
                 {:url    url
                  :params params}
                 :response
                 {:status       status,
                  :headers      headers,
                  :request-time request-time
                  :body         (str (subs body 0 100) "...")}})
          (refresh-access-token!)
          (log! {:msg "Retrying request ..."
                 :request
                 {:url url
                  :params params}})
          (make-request-raw! url (update-access-token params) (dec remaining-retries))))
      (catch #(not= 200 (:status %)) {:keys [status headers body request-time]}
        (log! {:msg "Request returned non-200 status code"
               :request
               {:url    url
                :params params}
               :reponse
               {:status       status
                :headers      headers
                :request-time request-time
                :body         (str (subs body 0 100) "...")}}))))))

(defn make-request!
  [url params]
  (-> (make-request-raw! url params)
      (:body)
      (as-> body
          (try
            (parse-json body)
            (catch Exception e
              (log! {:msg "Response could not be parsed JSON"
                     :request
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
                    with-user-agent
                    with-access-token)]
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
