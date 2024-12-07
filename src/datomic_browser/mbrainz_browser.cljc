(ns datomic-browser.mbrainz-browser
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric3-contrib :as ex]
            [hyperfiddle.electric-dom3 :as dom]
            [electric-fiddle.fiddle-index :refer [FiddleMain]]
            [datomic-browser.datomic-browser :refer [DatomicBrowser]]
            [models.mbrainz #?(:clj :as :cljs :as-alias) model]))

(e/defn Inject [?x #_& {:keys [Busy Failed Ok]}]
  ; todo needs to be a lot more sophisticated to inject many dependencies concurrently and report status in batch
  (cond
    (ex/None? ?x) Busy
    (nil? ?x) Failed
    () (e/Partial Ok ?x)))

(e/defn Inject-datomic [F]
  (e/server
    (Inject (e/Offload #(model/connect))
      {:Busy (e/fn [] (dom/h1 (dom/text "Waiting for Datomic connection ...")))
       :Failed (e/fn [] (dom/h1 (dom/text "Datomic transactor not found, see Readme.md")))
       :Ok F})))

(e/defn Fiddles []
  {`DatomicBrowser (Inject-datomic DatomicBrowser)})

(e/defn ProdMain [ring-req]
  (FiddleMain ring-req (Fiddles)
    :default `(DatomicBrowser)))
