(ns electric-fiddle.main
  (:require
   [hyperfiddle :as hf]
   [hyperfiddle.electric3 :as e :refer [$]]
   [hyperfiddle.electric-dom3 :as dom]
   [hyperfiddle.router3 :as r]
   [electric-fiddle.index :refer [Index]]
   ))

(e/defn NotFoundPage [& args] (e/client (dom/h1 (dom/text "Page not found: " (pr-str r/route)))))

(e/defn Entrypoint [f & args] (e/apply (get hf/pages f NotFoundPage) args))

(e/defn Main [ring-req]
  (binding [e/http-request (e/server ring-req)
            dom/node js/document.body]
    (dom/div ; FIXME wrapper div to circumvent v3 mount-point mounting nodes in reverse order if there are existing foreign DOM children.
      (r/router ($ r/HTML5-History)
        #_(dom/pre (dom/text (pr-str r/route)))
        (let [[f & _] r/route]
          (if-not f (r/ReplaceState! ['. `(Index)])
            (do
              (set! (.-title js/document) (str (some-> f name (str " – ")) "Electric Fiddle"))
              (case f
                `Index ($ Index)
                (r/pop
                  (binding [hf/Entrypoint Entrypoint] ; allow for recursive navigation
                    (hf/Entrypoint f)))))))))))
