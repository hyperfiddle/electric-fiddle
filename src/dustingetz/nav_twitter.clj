(ns dustingetz.nav-twitter
  (:import (java.security MessageDigest)
           [java.util Base64]
           [com.twitter.clientlib TwitterCredentialsOAuth2]
           [com.twitter.clientlib.api TwitterApi TweetsApi BookmarksApi]
           [java.util HashSet])
  (:require clj-http.client
            cheshire.core
            [dustingetz.caching-http-client :as http]
            [clojure.string :as str]
            [contrib.template :refer [comptime-resource]]))

(declare !!test-twitter)

(defn twitter [] !!test-twitter)
(defn tweets [x])
(defn users [x])

; https://developer.x.com/en/portal/products/free
; https://developer.x.com/en/portal/projects/1617251199111004165/apps/26575911/settings
; https://grok.com/chat/bcb1ba4e-0b98-48fb-a2d6-2e78e2a641ed

(defn generate-code-verifier []
  (let [bytes (byte-array 32)]
    (.nextBytes (java.security.SecureRandom.) bytes)
    (-> (Base64/getUrlEncoder) (.withoutPadding) (.encodeToString bytes))))

(defn code-challenge [verifier]
  (let [sha256 (MessageDigest/getInstance "SHA-256")
        hashed (.digest sha256 (.getBytes verifier "UTF-8"))
        encoder (.withoutPadding (Base64/getUrlEncoder))]
    (.encodeToString encoder hashed)))

(comment
  ; PKCE spec (RFC 7636 example) test values
  (code-challenge "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk") := "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
  (code-challenge (generate-code-verifier))
  )

(def redirect-uri "http://localhost:8080/callback")  ; Must match your app settings
(def scopes "tweet.read users.read bookmark.read like.read offline.access")

(defn local-auth-url [client-id verifier]
  (str "https://twitter.com/i/oauth2/authorize?"
    "response_type=code&client_id=" client-id
    "&redirect_uri=" redirect-uri
    "&scope=" (str/replace scopes " " "%20")
    "&state=state&code_challenge=" (code-challenge verifier)
    "&code_challenge_method=S256"))

(defn read-body
  ([reads res] (with-meta (reads (:body res)) res))
  ([res] (read-body cheshire.core/decode res)))

(comment
  ; login flow
  (def config (comptime-resource "dustingetz/twitter.edn"))
  (def verifier (generate-code-verifier))
  (local-auth-url (::oauth2-client-id config) verifier) ; open in browser, enter 2fa code, copy redirect link
  ; http://localhost:8080/callback?state=state&code=M0tHSDdTa2Q5UGU4TGZqYWpEeGJTdTdUNGJaQW9mRVpKaHVIaUhnNVlEZF8tOjE3NTQyNTM4MjA1OTY6MTowOmFjOjE
  (def auth-code "M0tHSDdTa2Q5UGU4TGZqYWpEeGJTdTdUNGJaQW9mRVpKaHVIaUhnNVlEZF8tOjE3NTQyNTM4MjA1OTY6MTowOmFjOjE") ; enter code from redirect
  (def r (clj-http.client/post "https://api.twitter.com/2/oauth2/token"
           {:debug true
            :basic-auth [(::oauth2-client-id config) (::oauth2-client-secret config)]
            :form-params {:code auth-code
                          :grant_type "authorization_code"
                          :client_id (::oauth2-client-id config)
                          :redirect_uri redirect-uri
                          :code_verifier verifier}}))
  (def x (read-body r))
  x
  (meta x)

  (def access-token (get x "access_token"))
  (def refresh-token (get x "refresh_token"))
  (def credentials
    (doto (TwitterCredentialsOAuth2.
            (::oauth2-client-id config)
            (::oauth2-client-secret config)
            access-token
            refresh-token)
      (.setOAUth2AutoRefreshToken true))) ; Enable auto-refresh

  (def !!test-twitter (delay (TwitterApi. credentials)))
  (def !users (.users @!!test-twitter))
  (def user (.getData (.execute (.findMyUser !users))))
  (.getUsername user) := "dustingetz"
  )



;(defn user-liked-tweets [!twitter user-id]
;  (let [tweet-fields (doto (HashSet.) (.add "id") (.add "text") (.add "created_at"))
;        o (.usersIdLikedTweets (.tweets !twitter) user-id nil nil nil nil nil tweet-fields nil nil nil)]
;    (.getData o))) ; offers .getId, .getText
;
;(defn user-bookmarked-tweets [!twitter user-id]
;  (let [tweet-fields (doto (HashSet.) (.add "id") (.add "text") (.add "created_at"))
;        resp (.getUsersIdBookmarks (.bookmarks !twitter) user-id nil nil nil nil nil tweet-fields nil nil nil)]
;    (.getData resp))) ; offers .getId, .getText
;
;; Handle exceptions with try-catch for ApiException.
;; For pagination, check (.getMeta resp) for nextToken and pass it to subsequent calls.
;; Rate Limits: Likes/bookmarks endpoints have limits (e.g., 75 requests/15 min for likes). Check X docs for details.
;; Fields/Expansions: Customize with sets for tweetFields, expansions, etc., to include author info or media.
;; Errors: Debug with .getMessage on exceptions. Ensure scopes include like.read and bookmark.read.
;; Examples/Refs: See full SDK docs in the GitHub repo for more methods. For a Java-focused guide (adaptable to Clojure interop), check the tutorial.
;; If you hit issues, the SDK source has examples under /examples.
