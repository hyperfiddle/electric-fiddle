(ns electric-tutorial.fiddles
  (:require [electric-fiddle.main]
            [electric-tutorial.tutorial :refer [Tutorial]]
            [hyperfiddle :as hf]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.router3 :as r]))

(e/defn Fiddles [] {`Tutorial Tutorial})

(e/defn FiddleMain [ring-req]
  (e/client
    (binding [dom/node js/document.body
              e/http-request (e/server ring-req)
              hf/pages (Fiddles)]
      (dom/div ; fixme
        (r/router (r/HTML5-History)
          (Tutorial))))))
