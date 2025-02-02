(ns dustingetz.entity-browser0
  "no major/breaking changes changes - used in the blog"
  (:require [clojure.datafy :refer [datafy]]
            [clojure.core.protocols :refer [nav]]
            [contrib.data :refer [unqualify index-of]]
            [dustingetz.easy-table :refer [Load-css]]
            [dustingetz.treelister2 :refer [explorer-seq]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric3-contrib :as ex]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms4 :refer [Intercept Interpreter Checkbox* TablePicker!]]
            [hyperfiddle.router4 :as router]
            [hyperfiddle.rcf :refer [tests]]))

(defn nav-in [m path]
  (loop [m m, path path]
    (if-some [[p & ps] (seq path)]
      (let [v (get m p)]
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
    (e/for [col (e/diff-by {} cols)]
      (if (Checkbox* true :label (unqualify col))
        col (e/amb)))))

#?(:clj (defmulti title (fn [m] (some-> m meta :clojure.datafy/class))))
#?(:clj (defmethod title :default [m] (some-> m meta :clojure.datafy/class str)))

#?(:clj (defmulti identify (fn [x])))
#?(:clj (defmethod identify :default [x] nil))

(e/defn TreeRow [[path value branch?]]
  #_(e/server (prn 'Q path name value)) ; all can be unserializable
  (let [name (e/server (peek path))]
    (e/client
      (dom/td (dom/props {:style {:padding-left (e/server (some-> path count dec (* 15) (str "px")))}})
        (dom/span (dom/props {:class "dustingetz-tooltip"}) ; hover anchor
          (dom/text (e/server (if (keyword? name) ; glitch
                                (unqualify name) (str name)))) ; label
          ; fixme tooltip bumps scrollHeight at bottom boundary by 4px, glitching scroll math
          #_(dom/span (dom/text (e/server (pr-str name))))))
      (dom/td
        (let [v-str (e/server (pr-str value))]
          (if (e/server (fn? value)) ; fns encode hyperlinks (on the server!)
            (dom/text "...")
            (dom/text (#(when-not branch? %) v-str))))))))

(e/defn TreeBlock [title m p-next]
  (e/client
    (dom/fieldset (dom/props {:class "entity"})
      (dom/legend (dom/text title))
      (let [xs! (e/server (ex/Offload-reset #(drop 1 (explorer-seq m))))
            row-count (e/server (count xs!)), row-height 24
            selected-x (e/server (first (filter (fn [[path _ _]] (= p-next path)) xs!)))] ; slow, but the documents are small
        (dom/props {:style {:--col-count 2 :--row-height row-height}})
        (Intercept (e/fn [index] (TablePicker! ::select index row-count
                                   (e/fn [index] (e/server (some-> (nth xs! index nil) TreeRow)))
                                   :row-height row-height))
          selected-x
          (e/fn Unparse [x] (e/server (index-of xs! x)))
          (e/fn Parse [index] (e/server (first (nth xs! index nil))))))))) ; keep path, drop value

(e/defn CollectionRow [cols ?x]
  (e/server
    (let [?m (ex/Offload-latch #(datafy ?x))] ; only visible rows
      (e/for [k cols]
        (dom/td (some-> ?m k pr-str dom/text))))))

(e/defn TableBlock [title xs! selected]
  (e/client
    (dom/fieldset (dom/props {:class "entity-children"})
      (let [xs! (e/server (vec xs!)), row-count (e/server (count xs!)), row-height 24
            cols (dom/legend (dom/text title)
                   (ColumnPicker (e/server (ex/Offload-reset #(-> xs! first datafy keys #_sort #_reverse)))))]
        (dom/props {:style {:--col-count (e/Count cols) :--row-height row-height}})
        (Intercept
          (e/fn [index] (TablePicker! ::select index row-count
                          (e/fn Row [index] (e/server (some->> (nth xs! index nil) (CollectionRow cols))))
                          :row-height row-height))
          selected
          (e/fn Unparse [p-next] (first p-next))
          (e/fn Parse [index] [(e/server (when (contains? xs! index) index))]))))))

(defn infer-block-type [x]
  (cond
    (map? x) :tree
    (set? x) :tree ; align with explorer-seq which indexes sets
    (or (sequential? x) (set? x)) :table
    () :scalar))

(e/defn Block [locus x]
  (e/client
    (when-some [F (e/server (case (infer-block-type x) :tree TreeBlock :table TableBlock :scalar nil nil))]
      (Interpreter {::select (e/fn [path] (router/Navigate! ['. (if path [path] [])])
                               [:hyperfiddle.electric-forms4/ok])}
        (let [title (e/server (or (title x) (pr-str (mapv #(if (keyword? %) (unqualify %) %) locus)) " "))
              locus (first router/route)]
          (F title x locus))))))

(e/defn BrowsePath [locus x]
  (e/client
    (Block locus x)
    (when-some [locus (first router/route)]
      (router/pop
        (e/for [locus (e/diff-by identity (e/as-vec locus))] ; don't reuse DOM/IO frames across different objects
          (let [x (e/server (ex/Offload-reset #(nav-in x locus)))]
            (BrowsePath locus x)))))))

(declare css)
(e/defn EntityBrowser0 [x]
  (e/client (dom/style (dom/text css)) (Load-css "dustingetz/easy_table.css")
    (dom/div (dom/props {:class (str "Browser dustingetz-EasyTable")})
      (let [x (e/server (ex/Offload-reset #(datafy x)))]
        (BrowsePath nil x)))))

(def css "
.Browser fieldset { position: relative; }
.Browser fieldset > .Viewport { height: calc(var(--row-height) * 15 * 1px); }
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