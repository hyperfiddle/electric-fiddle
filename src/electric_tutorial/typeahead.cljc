(ns electric-tutorial.typeahead
  (:require
    [dustingetz.str :refer [includes-str? abc-seq]]
    [hyperfiddle.electric-scroll0 :refer [Raster]]
    [hyperfiddle.electric3 :as e]
    [hyperfiddle.electric-dom3 :as dom]))

(e/defn Typeahead [v-id options-fn-S Option-label]
  (e/client
    (dom/div (dom/props {:class "Typeahead"})
      (let [!v-id (atom v-id) v-id (e/watch !v-id)
            !search (atom nil) search (e/watch !search)

            t (dom/input (dom/props {:placeholder "Filter..."})
                (let [[t err] (e/Token (dom/On "focus" identity nil))]
                  (if t
                    (reset! !search (dom/On "input" #(-> % .-target .-value) ""))
                    (dom/props {:value (str v-id)}))
                  t))
            is-open (some? t)]

        (when is-open
          (let [xs! (e/server (e/Offload (partial options-fn-S search)))]
            (dom/ul
              (e/for [i (Raster 0 20)] ; no virtual scroll - typeahead UX is filter biased
                (when-some [x (e/server (nth xs! i nil))]
                  (dom/li (e/server (Option-label x))
                    (dom/On "click" (fn [e] (doto e (.stopPropagation) (.preventDefault))
                                      (reset! !v-id x)
                                      (t))
                      nil)))))))
        v-id))))

(declare css)
(e/defn TypeaheadDemo []
  (dom/style (dom/text css))
  (let [xs! (e/server (abc-seq 3 17576)) ;; from "aaa" to "zzz"
        x (Typeahead ""
            (e/server (fn options [search] (filter #(includes-str? % search) xs!)))
            (e/fn Option-label [x] (dom/text (e/server (str x)))))]
    (dom/p (dom/text "Selected: " x))))

(def css "
/* layout */
.Typeahead { position: relative; }
.Typeahead ul { position: absolute; max-height: 200px; overflow: hidden; }
 /* cosmetic */
.Typeahead ul { background: white; border: 1px solid #ccc; list-style: none; padding: 0; }
.Typeahead li { padding: 8px; cursor: pointer; }
.Typeahead li:hover { background: #f5f5f5; }")
