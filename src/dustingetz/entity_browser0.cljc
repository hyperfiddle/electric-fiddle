(ns dustingetz.entity-browser0
  (:require [clojure.datafy :refer [datafy]]
            [clojure.core.protocols :refer [nav]]
            [contrib.data :refer [unqualify]]
            [dustingetz.easy-table :refer [TableScroll Load-css]]
            [dustingetz.flatten-document :refer [flatten-nested]]
            [hyperfiddle.electric3 :as e]
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

#?(:clj (defmulti identify (fn [x])))
#?(:clj (defmethod identify :default [x] nil))

(e/defn TreeRow [{:keys [path name value]}]
  (e/client
    (dom/td (dom/props {:style {:padding-left (some-> path count (* 15) (str "px"))}})
      (dom/span (dom/props {:class "dustingetz-tooltip"}) ; hover anchor
        (dom/text (unqualify name)) ; label
        (dom/span (dom/text (pr-str name)))))
    (dom/td
      (let [v-str (e/server (pr-str value))]
        (if (e/server (fn? value)) ; fns encode hyperlinks (on the server!)
          #_(router/link ['. [(conj path name)]]) (dom/text "...")
          (dom/text v-str))))))

(e/defn TreeBlock [p m p-next]
  (e/client
    (dom/fieldset (dom/props {:class "entity"})
      (dom/legend (dom/text (e/server (title m))))
      (let [xs! (e/server (flatten-nested m))
            selected-i (e/server (->> xs! ; slow, but the documents are small
                                   (map-indexed vector)
                                   (filter (fn [[i {:keys [path name]}]] (= p-next (conj path name))))
                                   (mapv first) first))]
        (when-some [?sel (TableScroll (e/server (count xs!))
                          (e/fn Row [i] (e/server (when-some [x (nth xs! i nil)]
                                                    (TreeRow x))))
                           selected-i)]

          (e/server
            (if-some [{:keys [path name value]} (nth xs! ?sel nil)]
              (conj path name))))))))

(e/defn CollectionRow [cols ?x]
  (e/server
    (let [?m (datafy ?x)] ; only visible rows
      (e/for [k cols]
        (dom/td (some-> ?m k pr-str dom/text))))))

(e/defn TableBlock [p xs! p-next]
  (e/server
    (dom/fieldset (dom/props {:class "entity-children"})
      (let [xs! (vec xs!)
            selected-i (first p-next) ; [0]
            colspec (dom/legend (dom/text (pr-str (mapv #(if (keyword? %) (unqualify %) %) p)) " ")
                      (ColumnPicker (some-> xs! first datafy keys)))
            cols (e/diff-by {} colspec)]
        (dom/props {:style {:--col-count (count colspec)}})
        (when-some [?sel (TableScroll (count xs!)
                           (e/fn Row [i] (when-some [x (nth xs! i nil)]
                                           (CollectionRow cols x)))
                           selected-i)]
          (if (contains? xs! ?sel) [?sel])))))) ; [i]

; object view: a tree, two column kv layout
; collection view: a table, dynamic columns with column picker
(e/defn Block [p-here x p-next]
  (e/client
    (e/client (prn 'Block p-here))
    (e/server (prn 'Block p-here (type x)))
    (if (e/server (sequential? x))
      (TableBlock p-here x p-next)
      (let [x (e/server (cond (nil? x) x (map? x) x () {::v x}))] ; scalars must have a column
        (TreeBlock p-here x p-next)))))

(e/defn BrowsePath [p-here x ps]
  #_(case #_(prn 'browse-path p-here #_(type x) hint ps router/route) (e/server (prn 'browse-path p-here (type x) hint ps)))
  (e/client
    (router/pop
      (if-some [?sel (Block p-here x (first ps))]
        (router/Navigate! ['. [?sel]])
        #_(router/Navigate! ['. []])) ; unstable, circuit effect bad
      (when-some [[p & ps] (doto (seq ps) (prn 'ps))]
        (let [x (e/server (nav-in x p))]
          (BrowsePath p x ps))))))

(e/defn Resolve [[tag id]]) ; userland should override

(declare css)
(e/defn EntityBrowser0 [uri & args]
  (e/client (dom/style (dom/text css)) (Load-css "dustingetz/easy_table.css")
    (dom/div (dom/props {:class (str "Browser dustingetz-EasyTable")})
      (let [x (e/server (datafy (Resolve uri)))]
        (BrowsePath uri x args)))))

(def css "
.Browser fieldset { position: relative; height: 25em; }
.Browser fieldset.entity          table { grid-template-columns: 15em auto; }
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