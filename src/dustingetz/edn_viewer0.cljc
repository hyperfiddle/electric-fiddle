(ns dustingetz.edn-viewer0
  (:require [clojure.datafy :refer [datafy]]
            [clojure.core.protocols :refer [nav]]
            [contrib.data :refer [unqualify]]
            [dustingetz.easy-table :refer [TableScroll Load-css]]
            [dustingetz.flatten-document :refer [flatten-nested]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric3-contrib :refer [Tap]]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms0 :refer [Checkbox*]]
            [hyperfiddle.router3 :as router]
            [hyperfiddle.rcf :refer [tests]]))

(defn nav-in [m path]
  (loop [m m, path path]
    (if-some [[p & ps] (seq path)]
      (let [v (get m p)
            v (if (fn? v) (v) v)] ; hyperlink == fn
        (recur (datafy (nav m p v)) ps)) ; todo revisit
      m)))

(tests
  (nav-in {} nil) := {}
  (nav-in {} []) := {}
  (nav-in {:a 1} [:a])
  (nav {:a 1} :a 1)
  (nav-in {:a {:b 2}} [:a :b]) := 2)

(e/defn ColumnPicker [cols]
  (e/client
    (let [state (e/for [col (e/diff-by {} cols)]
                  {col (Checkbox* true :label (unqualify col))})]
      (reduce-kv (fn [x k v] (if v (conj x k) x))
        [] (apply merge (e/as-vec state))))))

#?(:clj (defmulti title (fn [m] (some-> m meta :clojure.datafy/class))))
#?(:clj (defmethod title :default [m] (some-> m meta :clojure.datafy/class)))

(e/defn DocumentRow [{:keys [path name value]}]
  (e/client
    (dom/td (dom/props {:style {:padding-left (some-> path count (* 15) (str "px"))}})
      (dom/span (dom/props {:class "dustingetz-tooltip"}) ; hover anchor
        (dom/text (unqualify name)) ; label
        (dom/span (dom/text (pr-str name)))))
    (dom/td
      (let [v-str (e/server (pr-str value))]
        (if (e/server (fn? value)) ; fns encode hyperlinks (on the server!)
          (router/link ['. [(conj path name)]] (dom/text "..."))
          (dom/text v-str))))))

(e/defn CollectionRow [cols ?x]
  (e/server
    (let [?m (datafy ?x)] ; only visible rows
      (e/for [k cols]
        (dom/td (some-> ?m k pr-str dom/text))))))

(declare css)
(e/defn EntityBrowser [x ?focus-path]
  (dom/style (dom/text css)) (Load-css "dustingetz/easy_table.css")
  (dom/div (dom/props {:class (str "Browser dustingetz-EasyTable")})
    (e/server
      (let [m (datafy x)]

        (dom/fieldset (dom/props {:class "entity"})
          (let [xs! (flatten-nested m)]
            (dom/legend (dom/text (title m)))
            (TableScroll (count xs!)
              (e/fn Row [i] (when-some [x (nth xs! i nil)]
                              (DocumentRow x))))))

        (dom/fieldset (dom/props {:class "entity-children"})
          (let [xs! (if (seq ?focus-path) (nav-in m (seq ?focus-path)))
                colspec (dom/legend (dom/text (pr-str (mapv unqualify ?focus-path)) " ")
                          (ColumnPicker (some-> xs! first datafy keys)))
                cols (e/diff-by {} colspec)]
            (dom/props {:style {:--col-count (count colspec)}})
            (TableScroll (count xs!)
              (e/fn Row [i] (e/server (when-some [x (nth xs! i nil)]
                                        (CollectionRow cols x)))))))))))

(e/defn EdnViewer0
  ([] (EdnViewer0 (e/server (Tap)))) ; default to clojure tap> inspector
  ([x] (e/client (router/Apply (e/Partial EntityBrowser x) [nil])))) ; x is not route-bound

(def css "
.Browser fieldset.entity          { position:fixed; top:0;   bottom:50%; left:0; right:0; }
.Browser fieldset.entity-children { position:fixed; top:50%; bottom:0;   left:0; right:0; }
.Browser fieldset.entity          table { grid-template-columns: 20em auto; }
.Browser fieldset.entity-children table { grid-template-columns: repeat(var(--col-count), 1fr); }
.Browser fieldset table td a { font-weight: 600; }

/* table cell tooltips */
.Browser td {position: relative;}
.Browser .dustingetz-tooltip >       span { visibility: hidden; }
.Browser .dustingetz-tooltip:hover > span { visibility: visible; pointer-events: none; }
.Browser .dustingetz-tooltip > span {
  position: absolute; top: 20px; left: 10px; z-index: 2; /* interaction with row selection z=1 */
  margin-top: 4px; padding: 4px; font-size: smaller;
  box-shadow: 0 0 .5rem gray; border: 1px whitesmoke solid; border-radius: 3px; background-color: white; }")