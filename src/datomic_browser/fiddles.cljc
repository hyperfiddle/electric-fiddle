(ns datomic-browser.fiddles
  (:require [contrib.assert :refer [check]]
            #?(:clj [contrib.datomic-contrib :as dx])
            [electric-fiddle.fiddle-index :refer [pages]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.router3 :as r]
            [datomic-browser.datomic-browser :refer [conn db schema]]
            [models.mbrainz #?(:clj :as :cljs :as-alias) model]
            ;#?(:clj [models.teeshirt-orders-datomic :as model])
            ))

(e/defn DatomicBrowser []
  (e/server
    (let [x (e/Task (model/init-datomic) ::pending)]
      (case x
        ::pending (dom/h1 (dom/text "Waiting for Datomic connection ..."))
        ::model/ok (binding [conn (check model/*datomic-conn*)
                             db (check model/*datomic-db*)
                             schema (check (e/Task (dx/schema! model/*datomic-db*)))]
                     (e/client
                       (datomic-browser.datomic-browser/DatomicBrowser)))
        (do (dom/h1 (dom/text "Datomic transactor not found, see Readme.md"))
          (dom/code (dom/text x)))))))

(e/defn Fiddles []
  {`DatomicBrowser DatomicBrowser})

(e/defn ProdMain [ring-req]
  (e/client
    (binding [dom/node js/document.body
              e/http-request (e/server ring-req)
              pages (Fiddles)]
      (dom/div ; mandatory wrapper div https://github.com/hyperfiddle/electric/issues/74
        (r/router (r/HTML5-History)
          (DatomicBrowser))))))