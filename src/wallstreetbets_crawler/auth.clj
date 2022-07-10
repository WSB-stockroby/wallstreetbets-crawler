(ns wallstreetbets-crawler.auth
  (:require
   [clj-http.client :as client]
   [clojure.edn :as edn]
   [wallstreetbets-crawler.log :as log]
   [wallstreetbets-crawler.util :as util]))

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
    (->> (try
          (client/post "https://www.reddit.com/api/v1/access_token"
                       (with-user-agent
                         {:basic-auth [client-id client-secret]
                          :form-params
                          {:grant_type "refresh_token"
                           :refresh_token refresh-token}}))
          (catch Exception e
            (do
              (log/critical! "Couldn't retrieve refresh token"
                             (ex-data e))
              (throw e))))
         (:body)
         (util/parse-json)
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
    (log/info! "Access token refreshed")))

(comment
  (get-access-token!)
  (reset! access-token "asd")
  @access-token

  "asd")
