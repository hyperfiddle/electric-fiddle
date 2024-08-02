(ns electric-tutorial.fiddles
  (:require [electric-fiddle.main]
            [electric-tutorial.tutorial :refer [Tutorial]]
            [hyperfiddle :as hf]
            [hyperfiddle.electric-de :as e :refer [$]]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.router-de :as r]))

(e/defn Fiddles [] {`Tutorial Tutorial})

(e/defn FiddleMain [ring-req] ; FIXME ring-req is nil, even on server
  #_(e/server (binding [e/http-request ring-req])) ; FIXME make ring request available through the app, bugged, see `main.cljc`
  (e/client
    (binding [dom/node js/document.body
              hf/pages ($ Fiddles)]
      (r/router ($ r/HTML5-History)
        (e/server
          ($ Tutorial))))))
