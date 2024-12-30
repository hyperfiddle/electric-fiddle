(ns dustingetz.edn-viewer
  (:require [clojure.datafy :refer [datafy]]
            [clojure.core.protocols :refer [nav]]
            [contrib.assert :refer [check]]
            [contrib.data :refer [clamp-left]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms0 :refer [Checkbox*]]
            [hyperfiddle.router3 :as router]
            [hyperfiddle.electric-scroll0 :refer [Scroll-window IndexRing]]
            [dustingetz.flatten-document :refer [flatten-nested]]
            [missionary.core :as m]))

(e/defn TableScroll [?xs! Row]
  (e/server
    (dom/div (dom/props {:class "Viewport"})
      (let [record-count (count ?xs!)
            row-height 24
            [offset limit] (Scroll-window row-height record-count dom/node {:overquery-factor 1})]
        (dom/table (dom/props {:style {:position "relative" :top (str (* offset row-height) "px")}})
          (e/for [i (IndexRing limit offset)]
            (dom/tr (dom/props {:style {:--order (inc i)} :data-row-stripe (mod i 2)})
              (Row (datafy #_(nav ?xs! i) (nth (vec ?xs!) i nil))))))
        (dom/div (dom/props {:style {:height (str (clamp-left ; row count can exceed record count
                                                    (* row-height (- record-count limit)) 0) "px")}}))))))

(defn nav-in [m path]
  (loop [m m, path path]
    (if-some [[p & ps] (seq path)]
      (recur (datafy (nav m p (get m p))) ps)
      m)))

(comment
  (nav-in {} []) := {}
  (nav-in {:a 1} [:a])
  (nav {:a 1} :a 1)
  (nav-in {:a {:b 2}} [:a :b]) := 2)

(e/defn ColumnPicker [cols]
  (e/client
    (let [state (e/for [col (e/diff-by {} cols)]
                  {col (Checkbox* true :label col)})]
      (reduce-kv (fn [x k v] (if v (conj x k) x))
        [] (apply merge (e/as-vec state))))))

#?(:clj (defmulti title (fn [m] (some-> m meta :clojure.datafy/class))))
#?(:clj (defmethod title :default [m] (some-> m meta :clojure.datafy/class)))

(e/defn TwoPaneEntityFocus [x Row Row2]
  (let [m (e/server (datafy x))]
    (dom/fieldset (dom/props {:class "entity"})
      (dom/legend (dom/text (e/server (title m))))
      (TableScroll (e/server (flatten-nested m)) Row))
    (let [[?focus] router/route]
      #_(router/pop)
      (if (seq ?focus)
        (let [xs (e/server (nav-in m (seq ?focus)))]
          (dom/fieldset (dom/props {:class "children"})
            (let [cols (dom/legend (dom/text ?focus " ") (ColumnPicker (e/server (some-> xs first keys))))
                  xs (mapv #(select-keys % cols) xs)]
              (TableScroll xs Row2))))))))

(e/defn EntityRow [{:keys [path name value]}]
  (e/client
    (dom/td (dom/props {:style {:padding-left (some-> path count (* 15) (str "px"))}})
      (dom/text (str name)))
    (dom/td
      (if (= '... value)
        (router/link ['. [(conj path name)]] ; name is kw
          (dom/text (str value)))
        (dom/text value)))))

(e/defn DebugRow [?m] (dom/td (dom/text (some-> ?m pr-str))))

(e/defn Date_ [x] (dom/text (e/client (some-> x .toLocaleDateString))))
(e/defn String_ [x] (dom/text x))
(e/defn Edn [x] (dom/text (some-> x pr-str)))

(e/defn AutoRow [?m]
  (e/server
    (let [?m (datafy ?m)]
      (e/for [k (e/diff-by {} (some-> ?m keys))]
        (dom/td (Edn (some-> ?m k)))))))

#?(:clj (defonce *tap nil)) ; for repl & survive refresh
#?(:clj (def >tap (m/signal
                    (m/relieve {}
                      (m/observe
                        (fn [!]
                          (! *tap)
                          (let [! (fn [x] (def *tap x) (! x))]
                            (add-tap !)
                            #(remove-tap !))))))))

(declare css)
(e/defn EdnViewer []
  (e/client
    (dom/style (dom/text css))
    (dom/div (dom/props {:class (str "Explorer " #_(some-> page name))})
      (TwoPaneEntityFocus
        (e/server (e/input >tap))
        EntityRow
        AutoRow))))

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
/* Scroll machinery */
.Explorer .Viewport { overflow-x:hidden; overflow-y:auto; }
.Explorer table { display: grid; }
.Explorer table tr { display: contents; visibility: var(--visibility); }
.Explorer table td { grid-row: var(--order); }
.Explorer div.Viewport { height: 100%; }

/* Userland layout */
/* .Explorer fieldset { position:fixed; top:0em; bottom:0; left:0; right:0; } */
.Explorer fieldset.entity { position:fixed; top:0em; bottom:33vh; left:0; right:0; }
.Explorer fieldset.children { position:fixed; top:33vh; bottom:0; left:0; right:0; }
.Explorer table { grid-template-columns: 20em auto; }

/* Cosmetic */
.Explorer fieldset { padding: 0; padding-left: 0.5em; background-color: white; }
.Explorer legend { margin-left: 1em; font-size: larger; }
.Explorer table td { height: 24px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.Explorer table tr[data-row-stripe='0'] td { background-color: #f2f2f2; }
.Explorer table tr:hover td { background-color: #ddd; }
")
