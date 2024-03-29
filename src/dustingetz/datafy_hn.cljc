(ns dustingetz.datafy-hn
  (:require [clojure.core.protocols :as ccp :refer [nav]]
            [clojure.datafy :refer [datafy]]
            [clojure.spec.alpha :as s]
            #?(:clj [clj-http.client :as http])
            [contrib.data :refer [auto-props]]))

#?(:clj (deftype HNClient [root]))
#?(:clj (defrecord HNStory [hn])) ; don't enumerate attrs, use qualified kws
#?(:clj (defrecord HNComment [hn]))

(comment
  (http/get "https://hacker-news.firebaseio.com/v0/topstories.json?print=pretty" {:as :json})
  (def hn (datafy (HNClient. "https://hacker-news.firebaseio.com/v0/")))
  (def stories (nav hn ::topstories (::topstories hn)))
  (def story (datafy (first stories)))
  (def kids (nav story ::kids (::kids story)))
  (def kid (datafy (first kids)))
  (def kids (nav kid ::kids (::kids kid)))
  (def kid (datafy (first kids)))
  (def kids (nav kid ::kids (::kids kid)))
  (def kid (datafy (first kids)))
  (def kids (nav kid ::kids (::kids kid)))
  (def kid (datafy (first kids)))

  )

#?(:clj
   (extend-protocol ccp/Datafiable
     HNClient
     (datafy [^HNClient hn]
       (-> {::topstories `...
            ::newstories `...
            ::beststories `...}
         (with-meta
           {`ccp/nav
            (fn [x k v]
              (case k
                ::topstories (let [{:keys [status body]} (http/get (str (.-root hn) "topstories.json") {:as :json})
                                   ids body] ; response body is vec of id
                               (assert (= status 200))
                               (map #(-> (->HNStory hn) (assoc ::id %)) ids))
                ::beststories (let [{:keys [status body]} (http/get (str (.-root hn) "beststories.json") {:as :json})
                                    ids body]
                                (assert (= status 200))
                                (map #(assoc (->HNStory hn) ::id %) ids))
                ::newstories (let [{:keys [status body]} (http/get (str (.-root hn) "newstories.json") {:as :json})
                                   ids body]
                               (assert (= status 200))
                               (map #(assoc (->HNStory hn) ::id %) ids))
                v))})))

     HNStory
     (datafy [^HNStory o]
       (let [{:keys [status body]} (http/get (str (.-root (:hn o)) "item/" (::id o) ".json") {:as :json})]
         (assert (= status 200))
         (-> body auto-props (assoc ::kids `...)
           (with-meta
             {`ccp/nav
              (fn [x k v]
                (case k
                  ::kids (map #(assoc (->HNComment (:hn o)) ::id %) (:kids body))
                  v))}))))

     HNComment
     (datafy [^HNComment o]
       (let [{:keys [status body]} (http/get (str (.-root (:hn o)) "item/" (::id o) ".json") {:as :json})]
         (assert (= status 200))
         (-> body auto-props (assoc ::kids `...)
           (with-meta
             {`ccp/nav
              (fn [x k v]
                (case k
                  ::kids (map #(assoc (->HNComment (:hn o)) ::id %) (:kids body))
                  v))}))))))