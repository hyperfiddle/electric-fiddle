(ns dustingetz.edn-viewer0
  (:require [clojure.datafy :refer [datafy]]
            [clojure.core.protocols :refer [nav]]
            [contrib.data :refer [unqualify]]
            [dustingetz.easy-table :refer [TableScroll Load-css]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric3-contrib :refer [Tap]]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms0 :refer [Checkbox*]]
            [hyperfiddle.router3 :as router]
            [dustingetz.flatten-document :refer [flatten-nested]]))

(defn nav-in [m path]
  (loop [m m, path path]
    (if-some [[p & ps] (seq path)]
      (let [v (get m p)
            v (if (fn? v) (v) v)] ; hyperlink == fn
        (recur (datafy (nav m p v)) ps))
      m)))

(comment
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
        #_v-str ; ?
        (if (e/server (fn? value)) ; unserializable
          (router/link ['. (conj path name)] ; name is kw
            (dom/text "..."))
          (dom/text v-str))))))

(e/defn DebugRow [?m] (dom/td (dom/text (some-> ?m pr-str))))
(e/defn Date_ [x] (dom/text (e/client (some-> x .toLocaleDateString))))
(e/defn String_ [x] (dom/text x))
(e/defn Edn [x] (dom/text (some-> x pr-str)))

(e/defn CollectionRow [colspec ?x]
  (e/server
    (let [?m (datafy ?x)]
      (e/for [k (e/diff-by {} colspec)]
        (dom/td (Edn (some-> ?m k)))))))

(e/defn EntityBrowser
  #_([x] (router/ReplaceState! ['. [[] []]])) ; internal redirect if necessary
  ([x ?focus ?meta-focus]
   (e/server
     (let [m (datafy x)
           meta-m (datafy (type x))]

       (router/focus 0
         (dom/fieldset (dom/props {:class "entity"})
           (let [xs! (flatten-nested m)]
             (dom/legend (dom/text (title m)))
             (TableScroll (count xs!) (e/fn [i] (when-some [x (nth xs! i nil)] (DocumentRow x)))))))

       (router/focus 1
         (dom/fieldset (dom/props {:class "entity-meta"})
           (let [xs! (flatten-nested meta-m)]
             (dom/legend (dom/text (str (type x))))
             (TableScroll (count xs!) (e/fn [i] (when-some [x (nth xs! i nil)] (DocumentRow x)))))))

       (dom/fieldset (dom/props {:class "entity-children"})
         (let [xs! (if (seq ?focus) (nav-in m (seq ?focus)))
               colspec (dom/legend (dom/text (pr-str (mapv unqualify ?focus)) " ")
                         (ColumnPicker (some-> xs! first datafy keys)))
               n (count xs!)]
           (dom/props {:style {:--col-count (count colspec)}})
           (let [selected (TableScroll n
                            (e/fn [i]
                              (e/server
                                (when-some [x (nth xs! i nil)]
                                  (CollectionRow colspec x)))))]
             (e/server
               (when selected
                 (let [?x (nth xs! selected nil)]
                   ; todo clear route
                   #_(some-> ?x tap>)))))))

       (dom/fieldset (dom/props {:class "entity-meta-children"})
         (let [xs! (if (seq ?meta-focus) (nav-in meta-m (seq ?meta-focus)))
               colspec (dom/legend (dom/text (pr-str (mapv unqualify ?meta-focus)) " ")
                         (ColumnPicker (some-> xs! first datafy keys)))
               n (count xs!)]
           (dom/props {:style {:--col-count (count colspec)}})
           ; no self-nav in this view.
           (TableScroll n (e/fn [i] (when-some [x (nth xs! i nil)] (CollectionRow colspec x))))))))))

(declare css)
(e/defn EdnViewer0 []
  (e/client
    (dom/style (dom/text css)) (Load-css "dustingetz/easy_table.css")
    (dom/div (dom/props {:class (str "Browser dustingetz-EasyTable")})
      (router/Apply (e/Partial EntityBrowser (e/server (Tap))) ; x is not route-bound
        [[] []]))))

(comment
  (require 'cognitect.aws.client.api)
  (require 'dustingetz.datafy-git)
  #?(:clj (defmethod title 'org.eclipse.jgit.api.Git [m] (dustingetz.datafy-git/repo-path m)))
  #?(:clj (defmethod title 'cognitect.aws.client.impl.Client [m] (-> m :endpoint :hostname)))
  (tap> (cognitect.aws.client.api/client {:api :s3 :region "us-east-1"}))
  (tap> (dustingetz.datafy-git/load-repo "./"))
  *tap
  )

(def css "
.Browser fieldset.entity               { position:fixed; top:0;    bottom:50vh; left:0;    right:50vw; }
.Browser fieldset.entity-children      { position:fixed; top:50vh; bottom:0;    left:0;    right:50vw; }
.Browser fieldset.entity-meta          { position:fixed; top:0;    bottom:50vh; left:50vw; right:0; }
.Browser fieldset.entity-meta-children { position:fixed; top:50vh; bottom:0;    left:50vw; right:0; }

.Browser fieldset.entity table,
.Browser fieldset.entity-meta table          { grid-template-columns: 20em auto; }
.Browser fieldset.entity-children table,
.Browser fieldset.entity-meta-children table { grid-template-columns: repeat(var(--col-count), 1fr); }

/* table cell tooltips */
.Browser td {position: relative;}
.Browser .dustingetz-tooltip span { visibility: hidden; }
.Browser .dustingetz-tooltip:hover span { visibility: visible; pointer-events: none; }
.Browser .dustingetz-tooltip > span {
  position: absolute; top: 20px; left: 10px; z-index: 2; /* interaction with row selection z=1 */
  margin-top: 4px; padding: 4px; font-size: smaller;
  box-shadow: 0 0 .5rem gray; border: 1px whitesmoke solid; border-radius: 3px; background-color: white; }
")
