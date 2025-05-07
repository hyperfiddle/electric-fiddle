(ns electric-starter-app.main
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]))

(e/defn Main [ring-request]
  (e/client
    (binding [dom/node js/document.body]
      (dom/div ; mandatory wrapper div https://github.com/hyperfiddle/electric/issues/74
        (let [server? (dom/label (dom/text "Toggle client/server:")
                        (dom/input (dom/props {:type "checkbox", :checked true})
                          (dom/On "change" (fn [js-event] (-> js-event .-target .-checked)) true)))]
          (dom/pre (dom/text (if server?
                               (e/server (str "`1` on server is `" (class 1) "`"))
                               (e/client (str "`1` on client is `" (goog/typeOf 1) "`"))))))))))

(defn electric-boot [ring-request]
  #?(:clj  (e/boot-server {} Main (e/server ring-request))  ; inject server-only ring-request
     :cljs (e/boot-client {} Main (e/server (e/amb)))))     ; symmetric – same arity – no-value hole in place of server-only ring-request
