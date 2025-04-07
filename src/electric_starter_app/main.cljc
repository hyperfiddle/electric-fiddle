(ns electric-starter-app.main
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [electric-tutorial.chat-monitor :refer [ChatMonitor]]))

(e/defn Main [ring-request]
  (e/client
    (binding [dom/node js/document.body
              e/http-request (e/server ring-request)]
      ; mandatory wrapper div https://github.com/hyperfiddle/electric/issues/74
      (dom/div (dom/props {:style {:display "contents"}})
        (ChatMonitor)))))

(defn electric-boot [ring-req]
  #?(:clj  (e/boot-server {} Main (e/server ring-req))  ; inject server-only ring-request
     :cljs (e/boot-client {} Main (e/server (e/amb))))) ; symmetric – same arity – no-value hole in place of server-only ring-request