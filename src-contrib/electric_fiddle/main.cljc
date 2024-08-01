(ns electric-fiddle.main
  (:require
   [hyperfiddle :as hf]
   [hyperfiddle.electric-de :as e :refer [$]]
   [hyperfiddle.electric-dom3 :as dom]
   [hyperfiddle.router-de :as r]
   [electric-fiddle.index :refer [Index]]
   ))

(e/defn NotFoundPage [& args] (e/client (dom/h1 (dom/text "Page not found: " (pr-str r/route)))))

(e/defn Entrypoint [f & args] (e/apply (get hf/pages f NotFoundPage) args))

(e/defn Main [ring-req] ; FIXME ring-req is nil, even on server
  #_(e/server (binding [e/http-request ring-req])) ; FIXME crashes program in a loop with:
  ;; java.lang.NullPointerException: Cannot invoke "clojure.lang.IFn.invoke(Object)" because the return value of "clojure.lang.RT.aget(Object[], int)" is null
	;;   at hyperfiddle.electric_ring_adapter_de$electric_ws_handler$on_message__45818.invoke(electric_ring_adapter_de.clj:205)
  (e/client
    (binding [dom/node js/document.body]
      (r/router ($ r/HTML5-History)
        (dom/pre (dom/text (pr-str r/route)))
        (let [route       (or (ffirst r/route) `(Index)) ; route looks like {(f args) nil} or nil
              [f & args]  route]
          (set! (.-title js/document) (str (some-> f name (str " – ")) "Electric Fiddle"))
          (case f
            `Index ($ Index)
            (r/focus [route]
              (binding [hf/Entrypoint Entrypoint] ; allow for recursive navigation
                (e/apply hf/Entrypoint f args)))))))))



