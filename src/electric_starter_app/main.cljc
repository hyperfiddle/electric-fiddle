(ns electric-starter-app.main
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]))

(e/defn Main [_ring-request]
  (e/client
    (binding [dom/node js/document.body]
      ; mandatory wrapper div https://github.com/hyperfiddle/electric/issues/74
      (dom/div (dom/props {:style {:display "contents"}})
        (let [server? (dom/label
                        (dom/text "server")
                        (dom/input (dom/props {:type "checkbox"})
                                   (dom/On "change" #(-> % .-target .-checked) false)))]
          (dom/div
            (dom/text
              (if server?
                (e/server (str "1 on server is " (-> 1 class .getName)))
                (e/client (str "1 on client is " (-> 1 goog/typeOf)))))))))))

(defn electric-boot [ring-request]
  #?(:clj  (e/boot-server {} Main (e/server ring-request))  ; inject server-only ring-request
     :cljs (e/boot-client {} Main (e/server (e/amb))))) ; symmetric – same arity – no-value hole in place of server-only ring-request
