(ns heroicons.fiddles
  (:require
   contrib.str
   [heroicons.electric3.v24.outline]
   [heroicons.electric3.v24.solid]
   [hyperfiddle.electric3 :as e :refer [$]]
   [hyperfiddle.electric-css3 :as css]
   [hyperfiddle.electric-dom3 :as dom]
   [hyperfiddle.electric-svg3 :as svg]))

#?(:clj
   (defn query-icons [ns]
     (let [ns-name (name ns)]
       (some->> (find-ns ns)
         (clojure.core/ns-publics)
         (remove (fn [[k v]] (:macro (meta v))))
         (mapv (fn [[k v]] [(name k) (symbol ns-name (name (.sym v)))]))))))

(comment
  (query-icons 'heroicons.electric3.v24.outline)
  )

(defmacro all-icons [ns] (query-icons ns))

(e/defn AllIcons []
  {'heroicons.electric3.v24.outline (all-icons heroicons.electric3.v24.outline)
   'heroicons.electric3.v24.solid   (all-icons heroicons.electric3.v24.solid)})

(e/defn QueryIcons [ns needle]
  (->> (get (AllIcons) ns)
    (filter (fn [[k v]] (contrib.str/includes-str? k needle)))))

(e/defn IconSet [ns needle]
  (dom/div
    (dom/props {:class (css/scoped-style
                         (css/rule {:display               :grid
                                    :white-space           :nowrap
                                    :grid-template-columns "repeat(auto-fill, 15rem)"
                                    :grid-auto-rows        "5rem"}
                           (css/rule "span" {:display :flex, :flex-direction :column, :align-items :center})))})
    (e/for [[name Icon] (e/diff-by first (QueryIcons ns needle))]
      (dom/span
        (Icon (e/fn []))
        (dom/text name)))))

(e/defn Heroicons []
  (e/client
    (let [needle (dom/input (dom/props {:type :search, :value "circle"})
                            (dom/On "input" #(.. % -target -value) "circle"))]
      (dom/text "🔎")
      (dom/h2 (dom/text "Outline"))
      (IconSet 'heroicons.electric3.v24.outline needle)
      (dom/h2 (dom/text "Solid"))
      (IconSet 'heroicons.electric3.v24.solid needle))))

(e/defn Fiddles [] {`Heroicons Heroicons})

(e/defn FiddleMain [_ring-request]
  (e/client
    (binding [dom/node js/document.body] ; where to mount dom elements
      (dom/div
        ($ Heroicons)))))
