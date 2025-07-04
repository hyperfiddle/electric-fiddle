(ns dustingetz.edn-viewer0
  "used in /blog/y20250109_datafy"
  (:require [clojure.datafy :refer [datafy]]
            [clojure.core.protocols :refer [nav]]
            [contrib.data :refer [unqualify]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric3-contrib :refer [Tap]]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms5 :refer [Checkbox*]]
            [hyperfiddle.router4 :as router]
            [hyperfiddle.rcf :refer [tests]]))

(defn flatten-nested ; claude generated this trash, now superseded
  ([data] (flatten-nested data []))
  ([data path]
   (cond
     (map? data)
     (mapcat (fn [[k v]]
               (cond
                 (or (map? v) (set? v))
                 (cons {:path path :name k} (flatten-nested v (conj path k)))

                 ; render collections of records as hyperlinks
                 (and (sequential? v) (map? (first v)))
                 [{:path path :name k :value (constantly v)}] ; lift

                 ; render simple collections inline
                 (and (sequential? v) (not (map? (first v))))
                 [{:path path :name k :value v}]

                 #_#_(nil? v) [{:path path :name k}]
                 () [{:path path :name k :value v}]))
       data)

     ; render simple collections as indexed maps – only if simple collection is the root value
     (or (sequential? data) (set? data))
     (mapcat (fn [i v]
               (cond
                 (or (map? v) (sequential? v))
                 (cons {:path path :name i}
                   (flatten-nested v (conj path i)))

                 ()
                 [{:path path :name i :value v}]))
       (range) data)

     ; what else?
     () [{:path path :value data}])))

(comment
  (def test-data
    '{:response
      {:Owner
       {:DisplayName string
        :ID string}
       :Grants
       {:seq-of
        {:Grantee
         {:DisplayName string
          :EmailAddress string
          :ID string
          :Type [:one-of ["CanonicalUser" "AmazonCustomerByEmail" "Group"]]
          :URI string}
         :Permission [:one-of ["FULL_CONTROL" "WRITE" "WRITE_ACP" "READ" "READ_ACP"]]}}}})
  (flatten-nested test-data))

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
    (let [state (e/for [col (e/diff-by {} cols)]
                  {col (Checkbox* true :label (unqualify col))})]
      (reduce-kv (fn [x k v] (if v (conj x k) x))
        [] (apply merge (e/as-vec state))))))

#?(:clj (defmulti title (fn [m] (some-> m meta :clojure.datafy/class))))
#?(:clj (defmethod title :default [m] (some-> m meta :clojure.datafy/class)))

(e/defn DocumentRow [{:keys [path name value]}]
  (e/client
    (dom/td (dom/props {:style {:padding-left (some-> path count (* 15) (str "px"))}})
      (dom/span #_(dom/props {:class "dustingetz-tooltip"}) ; hover anchor
        (dom/text (cond (keyword? name) (unqualify name) () (str name))) ; label
        ; fixme tooltip bumps scrollHeight at bottom boundary by 4px, glitching scroll math
        #_(dom/span (dom/text (pr-str name)))))
    (dom/td
      (let [v-str (e/server (pr-str value))]
        (if (e/server (fn? value)) ; fns encode hyperlinks (on the server!)
          (router/link ['. [(conj path name)]] (dom/text "..."))
          (dom/text v-str))))))

(e/defn CollectionRow [cols ?x]
  (e/server
    (let [?m (e/Offload #(datafy ?x))] ; only visible rows
      (e/for [k cols]
        (dom/td (some-> ?m k pr-str dom/text))))))

(declare css)
(e/defn EntityBrowser [x ?focus-path]
  (dom/div (dom/props {:class (str "Browser")})
    (e/server
      (let [m (e/Offload #(datafy x))]

        (dom/fieldset (dom/props {:class "entity"})
          (let [xs! (flatten-nested m)]
            (dom/legend (dom/text (title m)))
            (e/client
              (hyperfiddle.electric-forms5/TablePicker* nil (e/server (count xs!))
                (e/fn Row [i] (e/server (when-some [x (nth xs! i nil)]
                                         (DocumentRow x)))))
              (e/amb))))

        (dom/fieldset (dom/props {:class "entity-children"})
          (let [xs! (if (seq ?focus-path) (nav-in m (seq ?focus-path)))
                colspec (dom/legend (dom/text (pr-str (mapv unqualify ?focus-path)) " ")
                          (ColumnPicker
                            (when-some [x0 (first xs!)]
                              (keys (datafy (nav xs! nil x0))))))
                cols (e/diff-by {} colspec)]
            (dom/props {:style {:--col-count (count colspec)}})
            (e/for [xs! (e/diff-by identity (e/as-vec xs!))] ; temporary conditional glitch workaround
              (e/client
                (hyperfiddle.electric-forms5/TablePicker* nil (e/server (count xs!))
                  (e/fn Row [i] (e/server (when-some [x (nth xs! i nil)]
                                            (CollectionRow cols (nav xs! nil x))))))
                (e/amb)
                ))))))))

(e/defn EdnViewer0
  ([] (EdnViewer0 (e/server (Tap)))) ; default to clojure tap> inspector
  ([x] (e/client (e/Apply EntityBrowser x (or (seq router/route) [nil])))))

(def css (str hyperfiddle.electric-forms5/css "
.Browser fieldset.entity          { position:fixed; top:0;   bottom:50%; left:0; right:0; }
.Browser fieldset.entity-children { position:fixed; top:50%; bottom:0;   left:0; right:0; }
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
  box-shadow: 0 0 .5rem gray; border: 1px whitesmoke solid; border-radius: 3px; background-color: white; }"))
