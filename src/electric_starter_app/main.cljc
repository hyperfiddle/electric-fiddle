(ns electric-starter-app.main
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]))

(e/defn Main [ring-request]
  (e/client
    (binding [dom/node js/document.body] ; DOM nodes will mount under this one
      (dom/div ; mandatory wrapper div to ensure node ordering - https://github.com/hyperfiddle/electric/issues/74
        (let [server? (dom/label (dom/text "Toggle client/server:")
                        (dom/input (dom/props {:type "checkbox", :checked true})
                          ;; return checked state of input, start `true`
                          (dom/On "change" (fn [js-event] (-> js-event .-target .-checked)) true)))]
          (dom/pre (dom/text (if server?
                               (e/server (str "`1` on server is `" (class 1) "`"))
                               (e/client (str "`1` on client is `" (goog/typeOf 1) "`"))))))
        (dom/hr)
        (dom/p (dom/text "Source code is in ") (dom/code (dom/text "src/electric_starter_app/main.cljc")))
        (dom/p (dom/text "Check out ") (dom/a (dom/text "Electric examples")
                                         (dom/props {:href "https://electric.hyperfiddle.net" :target "_blank"})))))))

(defn electric-boot [ring-request]
  #?(:clj  (e/boot-server {} Main (e/server ring-request))  ; inject server-only ring-request
     :cljs (e/boot-client {} Main (e/server (e/amb)))))     ; symmetric – same arity – no-value hole in place of server-only ring-request
