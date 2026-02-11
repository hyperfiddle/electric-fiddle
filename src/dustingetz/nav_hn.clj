(ns dustingetz.nav-hn
  (:require
    [clj-http.client :as http]
    [contrib.data :refer [auto-props]]
    [hyperfiddle.hfql0 :as hfql]))

(defprotocol HN
  (topstories [o])
  (newstories [o])
  (beststories [o])
  (item [o id])
  (kids [o]))

(defrecord HNEntity [client] ; also, ::id
  HN
  (kids [o] (-> (item (.-client o) (::id o)) (assoc ::id (::id o)))))

(deftype HNClient [root]
  HN
  (topstories [o]
    (let [{:keys [status body]} (http/get (str root "topstories.json") {:as :json})
          ids body] ; response body is vec of id
      (assert (= status 200))
      (mapv #(assoc (->HNEntity o) ::id %) ids)))

  (beststories [o]
    (let [{:keys [status body]} (http/get (str root "beststories.json") {:as :json})
          ids body]
      (assert (= status 200))
      (mapv #(assoc (->HNEntity o) ::id %) ids)))

  (newstories [o]
    (let [{:keys [status body]} (http/get (str root "newstories.json") {:as :json})
          ids body]
      (assert (= status 200))
      (mapv #(assoc (->HNEntity o) ::id %) ids)))

  (item [o id]
    (let [{:keys [status body]} (http/get (str root "item/" id ".json") {:as :json})]
      (assert (= status 200))
      (auto-props body)))) ; ::kids, etc

(defn hn [] (HNClient. "https://hacker-news.firebaseio.com/v0/"))

(extend-protocol hfql/Suggestable
  HNClient (-suggest [_] (hfql/pull-spec [type]))
  HNEntity (-suggest [_] (hfql/pull-spec [type ::id kids])))