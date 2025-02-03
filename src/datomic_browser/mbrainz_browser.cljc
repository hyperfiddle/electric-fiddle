(ns datomic-browser.mbrainz-browser
  (:require #?(:clj datomic.api)
            [datomic-browser.datomic-browser :refer [Inject-datomic]]
            #?(:clj dustingetz.mbrainz)
            [electric-fiddle.fiddle-index :refer [FiddleMain]]
            [hyperfiddle.electric3 :as e]))

(e/defn DatomicBrowser
  ([] (e/call (Inject-datomic dustingetz.mbrainz/mbrainz-uri
                datomic-browser.datomic-browser/DatomicBrowser))))

(e/defn Fiddles []
  {`DatomicBrowser DatomicBrowser})

(e/defn ProdMain [ring-req]
  (FiddleMain ring-req (Fiddles)
    :default `(DatomicBrowser)))
