(ns datomic-browser.mbrainz-browser
  (:require #?(:clj [clojure.tools.logging :as log])
            [contrib.assert :refer [check]]
            #?(:clj datomic.api)
            [datomic-browser.datomic-browser :refer [DatomicBrowser]]
            #?(:clj dustingetz.mbrainz)
            [electric-fiddle.fiddle-index :refer [FiddleMain]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric3-contrib :as ex]
            [hyperfiddle.electric-dom3 :as dom]
            [missionary.core :as m]))

(e/defn Inject [?x #_& {:keys [Busy Failed Ok]}]
  ; todo needs to be a lot more sophisticated to inject many dependencies concurrently and report status in batch
  (cond
    (ex/None? ?x) Busy
    (or (some? (ex-message ?x)) (nil? ?x)) (Failed ?x)
    () (e/Partial Ok ?x)))

(e/defn Inject-datomic [datomic-uri F]
  (e/server
    (Inject (e/Task (m/via m/blk
                      (try (check (datomic.api/connect datomic-uri))
                           (catch Exception e (log/error e) e))))
      {:Busy (e/fn [] (dom/h1 (dom/text "Waiting for Datomic connection ...")))
       :Failed (e/fn [err] (dom/h1 (dom/text "Datomic transactor not found, see Readme.md"))
                 (dom/pre (dom/text (pr-str err))))
       :Ok F})))

(e/defn DatomicBrowser*
  ([] (e/call (Inject-datomic dustingetz.mbrainz/mbrainz-uri DatomicBrowser))))

(e/defn Fiddles []
  {`DatomicBrowser DatomicBrowser*})

(e/defn ProdMain [ring-req]
  (FiddleMain ring-req (Fiddles)
    :default `(DatomicBrowser)))
