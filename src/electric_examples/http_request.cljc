(ns electric-examples.http-request
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [dustingetz.str :refer [pprint-str]]))

(e/defn HttpRequest []
  (e/client
    (dom/pre (dom/text (e/server (pprint-str e/http-request))))))

(e/defn Main [ring-request]
  (binding [dom/node (e/client js/document.body) ; DOM nodes will mount under this one
            e/http-request (e/server ring-request)]
    (e/client
      (dom/div ; mandatory wrapper div to ensure node ordering - https://github.com/hyperfiddle/electric/issues/74 
        (HttpRequest)))))

(defn electric-boot [ring-request]
  #?(:clj  (e/boot-server {} Main (e/server ring-request))  ; inject server-only ring-request
     :cljs (e/boot-client {} Main (e/server (e/amb)))))     ; symmetric – same arity – no-value hole in place of server-only ring-request
