(ns electric-fiddle.fiddle-index
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.router3 :as r]))

(e/declare pages) ; inject so FiddleIndex is routable as a fiddle, also used by tutorial

(e/defn NotFoundPage [& args]
  (e/client
    (dom/h1 (dom/text "Page not found"))
    (dom/p (dom/text "Probably we broke URLs, sorry! ")
      (r/link ['/ []] (dom/text "index")))))

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
  [fiddles
   & {:keys [default]
      :or {default `(FiddleIndex)}}]
  #_(dom/pre (dom/text (pr-str r/route)))
  (let [[fiddle & _] r/route]
    (if-not fiddle (r/ReplaceState! ['. default])
      (let [Fiddle (get fiddles fiddle NotFoundPage)]
        (set! (.-title js/document) (str (some-> fiddle name (str " – ")) "Electric Fiddle"))
        (binding [pages fiddles] ; todo untangle - tutorial uses some fiddle infrastructure, perhaps should use more?
          (case fiddle
            `FiddleIndex #_(FiddleIndex) (Fiddle) ; lol why - workaround crash 20241205
            (r/pop (Fiddle))))))))
