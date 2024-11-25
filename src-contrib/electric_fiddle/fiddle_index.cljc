(ns electric-fiddle.fiddle-index
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.router3 :as r]))

(def pages) ; inject, binding fiddles in entrypoint fixes comptime stackoverflow

(e/defn NotFoundPage [& args] (e/client (dom/h1 (dom/text "Page not found: " (pr-str r/route)))))

(e/defn Entrypoint [fiddle & args] (e/apply (get pages fiddle NotFoundPage) args))

(e/defn FiddleIndex []
  (e/client
    (dom/h1 (dom/text `FiddleIndex))
    ;; (dom/pre (dom/text (pr-str r/route)))
    (e/cursor [[k _] (e/diff-by key (sort-by key pages))]
      (dom/div
        (r/link [k] (dom/text (name k)))
        (dom/text " " k)))))
