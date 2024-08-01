(ns electric-fiddle.main
  (:require
   [hyperfiddle :as hf]
   [hyperfiddle.electric-de :as e :refer [$]]
   [hyperfiddle.electric-dom3 :as dom]
   ;; [hyperfiddle.router :as r]
   ;; [electric-fiddle.index :refer [Index]]
   ))

(e/defn NotFoundPage [& args] (e/client (dom/h1 (dom/text "Page not found: " #_(pr-str r/route)))))

(e/defn Entrypoint [f & args]
  (prn "Entrypoint" f args)
  ;; (prn "Entrypoint" f hf/pages)
  #_($ hello-fiddle.fiddles/Hello)
  (let [F (get hf/pages f NotFoundPage)]
    (dom/pre (dom/text F))
    (e/apply F [] #_args)))

#_
(e/defn Main [ring-req]
  (e/server
    (binding [e/http-request ring-req]
      (e/client
        (binding [dom/node js/document.body]
          (r/router (r/HTML5-History.)
            (dom/pre (dom/text (pr-str r/route)))
            (let [route       (or (ffirst r/route) `(Index)) ; route looks like {(f args) nil} or nil
                  [f & args]  route]
              (set! (.-title js/document) (str (some-> f name (str " – ")) "Electric Fiddle"))
              (case f
                `Index (Index.)
                (r/focus [route]
                  (binding [hf/Entrypoint Entrypoint] ; allow for recursive navigation
                    (e/apply hf/Entrypoint f args)))))))))))

(e/defn Main [ring-req]
  #_(e/server (binding [e/http-request ring-req])) ; TODO implement e/http-request
  (e/client
    (binding [dom/node js/document.body
              #_#_hf/Entrypoint Entrypoint]
      (dom/p (dom/text "main"))
      (e/apply Entrypoint [`hello-fiddle.fiddles/Hello]))))



