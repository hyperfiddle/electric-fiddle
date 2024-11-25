(ns electric-fiddle.main
  (:require
   [hyperfiddle.electric3 :as e]
   [hyperfiddle.electric-dom3 :as dom]
   [hyperfiddle.router3 :as r]
   [electric-fiddle.fiddle-index :refer
    [FiddleIndex Entrypoint pages]]))

(e/defn Main [ring-req]
  (binding [e/http-request (e/server ring-req)
            dom/node js/document.body]
    (dom/div ; mandatory wrapper div https://github.com/hyperfiddle/electric/issues/74
      (r/router (r/HTML5-History)
        #_(dom/pre (dom/text (pr-str r/route)))
        (let [[fiddle & _] r/route]
          (if-not fiddle (r/ReplaceState! ['. `(FiddleIndex)])
            (do
              (set! (.-title js/document) (str (some-> fiddle name (str " – ")) "Electric Fiddle"))
              (case fiddle
                `FiddleIndex (FiddleIndex)
                (r/pop
                  (Entrypoint fiddle))))))))))
