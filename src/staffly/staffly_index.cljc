(ns staffly.staffly-index
  (:require #?(:clj [backtick :refer [template]])
            [clojure.core.match :refer [match]]
            clojure.set
            [contrib.data :refer [unqualify]]
            contrib.str
            #?(:clj [datomic.api :as d])
            [dustingetz.easy-table :refer [EasyTable]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms3 :refer [Input]]
            [hyperfiddle.ui.typeahead :refer [Typeahead]]
            [hyperfiddle.router4 :as r]
            [staffly.staffly-model :as model]))

(defn entity-type [m]
  (match m
    {:staff/id id} ::staff
    {:venue/id id} ::venue))

(defn entity-type-options [s]
  [::staff
   ::venue])

(defn type->filter [?type]
  (case ?type
    ::staff '[?e :staff/id]
    ::venue '[?e :venue/id]
    nil '(or [?e :staff/id]
           [?e :venue/id])))

#?(:clj (defn index-by-name [search type_]
          (d/q (template
                 [:find [?e ...] :where
                  ~@(list (type->filter type_))
                  (or [?e :staff/name ?name] [?e :venue/name ?name])
                  [(contrib.str/includes-str? ?name ?search)]
                  :in $ ?search])
            model/*db* search)))

#?(:clj (defn index-by-email [search type_]
          (d/q (template
                 [:find [?e ...] :where
                  ~@(list (type->filter type_))
                  (or [?e :staff/email ?email] [?e :venue/email ?email])
                  [(contrib.str/includes-str? ?email ?needle)]
                  :in $ ?needle])
            model/*db* search)))

#?(:clj (defn index
          [search type_
           & {:keys [limit]
              :or {limit 20}}]
          (let [by-name (future (index-by-name search type_))
                by-email (future (index-by-email search type_))]
            (into []
              (comp
                (map (fn [e] (d/pull model/*db* [:staff/id :venue/id :db/id :staff/name :venue/name :staff/email] e)))
                (map (fn [e] (merge e
                               {:entity/name (or (:staff/name e) (:venue/name e))}
                               (case (entity-type e)
                                 ::staff (d/pull model/*db* [:staff/events-worked :staff/venue-rating] (:db/id e))
                                 ::venue (d/pull model/*db* [:venue/capacity :venue/rating] (:db/id e))
                                 nil))))
                (if (some? limit) (take limit) (map identity)))
              (clojure.set/union (set @by-name) (set @by-email))))))

(comment
  (count (index "" ::staff :limit nil))
  (count (index "" ::venue :limit nil))
  (count (index-by-name "" ::staff))
  (count (index-by-name "" ::venue))
  (count (index-by-email "" ::staff))
  (count (index-by-email "" ::venue)))

(defmacro Field [name_ & body]
  `(do (dom/dt (dom/text ~name_))
     (dom/dd ~@body)))

(declare css)

(e/defn Index []
  (e/client
    (dom/style (dom/text css))
    (dom/h1 (dom/text (dom/text "Staffly Index")))
    (r/focus [0]
      (let [type_ (dom/dl
                    (Field "type" (Typeahead ::staff ; initial value for fast load
                                    :Options (e/fn [s] (e/server (e/diff-by identity (entity-type-options s))))
                                    :option-label (fn [x] (some-> x name)))))]
        #_(e/server (prn 'q search type_)) ; search is too slow, todo filter in memory w/ treelister
        (e/server
          (EasyTable "Index"
            (fn query [search] (index search type_ :limit nil))
            (e/fn [e]
              (e/server
                (let [type_ (e/server (some-> e entity-type))]
                  (e/for [a (e/diff-by {} [:db/id ::entity-type])]
                    (let [v (a e)]
                      (dom/td
                        (case a
                          :db/id (r/link ['.. '.. [(some-> type_ unqualify) v]] (dom/text (some-> v pr-str)))
                          ::entity-type (dom/text (some-> type_ name))
                          (dom/text (pr-str v)))))))))))))))


(def css "
html:has(.staffly.dustingetz-EasyTable){height:100%; box-sizing: border-box;}
.staffly.dustingetz-EasyTable{ position:relative; height:100%; }

.staffly.dustingetz-EasyTable fieldset:has(> .Viewport) {flex: 1; max-height: 22rem;}
.staffly.dustingetz-EasyTable .Viewport {height: 100%}
.staffly.dustingetz-EasyTable fieldset .Viewport table {grid-template-columns: 1fr 1fr;}
")