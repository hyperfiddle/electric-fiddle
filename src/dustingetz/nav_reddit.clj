(ns dustingetz.nav-reddit
  (:import
   (net.dean.jraw RedditClient)
   (net.dean.jraw.models Account)
   (net.dean.jraw.http OkHttpNetworkAdapter UserAgent)
   (net.dean.jraw.oauth OAuthHelper Credentials))
  (:require
   [contrib.assert :refer [check]]
   [contrib.template :refer [comptime-resource]]))

(def config (comptime-resource "dustingetz/nav_reddit.edn"))

; https://www.reddit.com/dev/api/
; https://www.reddit.com/prefs/apps
; https://github.com/mattbdean/JRAW

(defn reddit "password auth for 'script' app type"
  []
  (let [{::keys [username password client-id client-secret]} (check config)
        user-agent (UserAgent. "bot" "my-clojure-bot" "v0.1" username)
        creds (Credentials/script username password client-id client-secret)
        adapter (OkHttpNetworkAdapter. user-agent)]
    (OAuthHelper/automatic adapter creds)))

(declare !reddit)

(defn me [] (.me !reddit)) ; todo inject
(defn me-about [] (.about (.me !reddit)))


(comment
  (def !reddit (reddit))
  (def me (.about (.me !reddit)))
  (.getName me)
  (.getLinkKarma me)
  (.getCommentKarma me)
  )