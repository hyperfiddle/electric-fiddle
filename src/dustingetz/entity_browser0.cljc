(ns dustingetz.entity-browser0
  (:require [clojure.datafy :refer [datafy]]
            [clojure.core.protocols :refer [nav]]
            [contrib.data :refer [unqualify index-of]]
            [dustingetz.easy-table :refer [TableScroll Load-css]]
            [dustingetz.flatten-document :refer [flatten-nested explorer-seq]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric3-contrib :as ex]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms4 :refer [Intercept Interpreter Checkbox* TablePicker!]]
            [hyperfiddle.electric3-contrib :as ex]
            [hyperfiddle.router4 :as router]
            [hyperfiddle.rcf :refer [tests]]))

(defn nav-in [m path] #_(prn 'nav-in path m)
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
#?(:clj (defmethod title :default [m] (some-> m meta :clojure.datafy/class)))

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
                        (dom/span (dom/text (e/server (pr-str name))))))
      (dom/td
        (let [v-str (e/server (pr-str value))]
          (if (e/server (fn? value)) ; fns encode hyperlinks (on the server!)
            #_(router/link ['. [(conj path (e/server name))]]) (dom/text "...")
            (dom/text (#(when-not branch? %) v-str))))))))

(e/defn TreeBlock [p m p-next]
  (e/client
    (dom/fieldset (dom/props {:class "entity"})
      (dom/legend (dom/text (e/server (or (title m) (pr-str (mapv #(if (keyword? %) (unqualify %) %) p)) " "))))
      (let [xs! (e/server (explorer-seq m))
            selected-x (e/server (first (filter (fn [[path _ _]] (= p-next path)) xs!)))] ; slow, but the documents are small
        (Intercept (e/fn [index] (TablePicker! ::select index (e/server (count xs!))
                                   (e/fn [index] (e/server (some-> (nth xs! index nil) TreeRow)))))
          selected-x
          (e/fn Unparse [x] (e/server (index-of xs! x)))
          (e/fn Parse [index] (e/server (first (nth xs! index nil))))))))) ; keep path, drop value

(e/defn CollectionRow [cols ?x]
  (e/server
    (let [?m (ex/Offload-latch #(datafy ?x))] ; only visible rows
      (e/for [k cols]
        (dom/td (some-> ?m k pr-str dom/text))))))

(e/defn TableBlock [p xs! p-next]
  (e/client
    (dom/fieldset (dom/props {:class "entity-children"})
      (let [xs! (e/server ((fn [] (try (vec xs!) (catch Exception _ []))))) ; glitch
            cols (dom/legend (dom/text (pr-str (mapv #(if (keyword? %) (unqualify %) %) p)) " ")
                   (ColumnPicker (e/server (ex/Offload-reset #(some-> xs! first datafy keys #_sort #_reverse)))))] ; unstable
        (dom/props {:style {:--col-count (e/Count cols)}})
        (Intercept
          (e/fn [index] (TablePicker! ::select index (e/server (count xs!))
                          (e/fn Row [index] (e/server (some->> (nth xs! index nil) (CollectionRow cols))))))
          p-next
          (e/fn Unparse [p-next] (first p-next))
          (e/fn Parse [index] [(e/server (when (contains? xs! index) index))]))))))

(defn infer-block-type [x]
  (let [q (cond
            (map? x) :tree
            (or (sequential? x) (set? x)) :table
            () :scalar)]
    (prn 'q q (type x) (pr-str x))
    q))

(e/defn Block [p-here x p-next]
  (e/client ; server causes reboot on first select?
    (let [btype (e/server (infer-block-type x))]
      (e/client (prn 'Block p-here btype))
      (e/server (prn 'Block p-here btype))
      (case btype ; client, don't lag selection on the way out
        :tree (TreeBlock p-here x p-next)
        :table (TableBlock p-here x p-next)
        :scalar nil))))

(e/defn BrowsePath [p-here x ps]
  (e/client
    (router/pop
      (Interpreter {::select (e/fn [path]
                               (if path
                                 (router/Navigate! ['. [path]])
                                 (router/Navigate! ['. []]))
                               [:hyperfiddle.electric-forms4/ok])}
        (Block p-here x (first ps)))
      (when-some [[p & ps] (seq ps)]
        (let [x (e/server (nav-in x p))]
          (BrowsePath p x ps))))))

(e/defn Resolve [[tag id]]) ; inject

(declare css)
(e/defn EntityBrowser0 [uri & args]
  (e/client (dom/style (dom/text css)) (Load-css "dustingetz/easy_table.css")
    (dom/div (dom/props {:class (str "Browser dustingetz-EasyTable")})
      (let [x (e/server (Resolve uri))
            x (e/server (ex/Offload-latch #(datafy x)))]
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