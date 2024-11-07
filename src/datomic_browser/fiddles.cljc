(ns datomic-browser.fiddles
  (:require [contrib.assert :refer [check]]
            #?(:clj [contrib.datomic-contrib :as dx])
            [hyperfiddle :as hf]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.router3 :as r]
            [datomic-browser.datomic-browser :refer [conn db schema]]
            #?(:clj [models.mbrainz :as model])
            ;#?(:clj [models.teeshirt-orders-datomic :as model])
            ))

(e/defn DatomicBrowser []
  (e/server
    (let [[conn db] (model/init-datomic)
          schema (check (e/Task (dx/schema! db)))]
      (binding [conn (check conn)
                db (check db)
                schema (check schema)]
        (e/client
          (datomic-browser.datomic-browser/DatomicBrowser))))))

(e/defn Fiddles []
  {`DatomicBrowser DatomicBrowser})

(e/defn FiddleMain [ring-req]
  (e/client
    (binding [dom/node js/document.body
              e/http-request (e/server ring-req)
              hf/pages (Fiddles)]
      (dom/div ; fixme
        (r/router (r/HTML5-History)
          (DatomicBrowser))))))