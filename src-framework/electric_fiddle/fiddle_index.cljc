(ns electric-fiddle.fiddle-index
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.router3 :as r]))

(e/declare pages) ; inject, binding fiddles in entrypoint fixes comptime stackoverflow

(e/defn NotFoundPage [& args]
  (e/client
    (dom/h1 (dom/text "Page not found"))
    (dom/p (dom/text "Probably we broke URLs, sorry! ")
      (r/link ['/ []] (dom/text "index")))))

(e/defn Entrypoint [fiddle & args]
  (e/apply (get pages fiddle NotFoundPage) args))

(e/defn FiddleIndex []
  (e/client
    (dom/h1 (dom/text `FiddleIndex))
    ;; (dom/pre (dom/text (pr-str r/route)))
    (dom/table (dom/props {:class "hyperfiddle-index"})
      (e/for [[k F] (e/diff-by key (sort-by key pages))]
        (dom/tr
          (dom/td (r/link [k] (dom/text (name k))))
          (dom/td (dom/text k)))))))

(e/defn FiddleRoot ; also used in prod tutorial, which leverages the dev fiddle infrastructure
  [& {:keys [default]
      :or {default `(FiddleIndex)}}]
  #_(dom/pre (dom/text (pr-str r/route)))
  (let [[fiddle & _] r/route]
    (if-not fiddle (r/ReplaceState! ['. default])
      (do
        (set! (.-title js/document) (str (some-> fiddle name (str " – ")) "Electric Fiddle"))
        (case fiddle
          `FiddleIndex (FiddleIndex)
          (r/pop
            (Entrypoint fiddle)))))))
