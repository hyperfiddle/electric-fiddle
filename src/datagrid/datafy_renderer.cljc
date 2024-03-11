(ns datagrid.datafy-renderer
  (:require
   [clojure.datafy :refer [datafy nav]]
   [datagrid.collection-editor :as ce]
   [datagrid.datagrid :as dg]
   [datagrid.stage :as stage]
   [datagrid.styles :as styles]
   [datagrid.virtual-scroll :as vs]
   [hyperfiddle.electric :as e]
   [hyperfiddle.electric-dom2 :as dom]
   [hyperfiddle.electric-ui4 :as ui]
   [clojure.string :as str]
   [malli.core :as malli]
   [malli.registry :as reg])
  (:import
   (hyperfiddle.electric Pending))
  #?(:cljs (:require-macros datagrid.datafy-renderer)))


(e/def loading? false)

(e/defn* CellInput [value OnCommit]
  (e/client
    (stage/staged OnCommit
      (dom/input (dom/props {:type :text :class "cell-input"})
                 (set! (.-value dom/node) value)
                 (dom/on "blur" (e/fn* [_] (when (some? stage/stage) (stage/Commit. stage/stage))))
                 (dom/on! "keydown" (fn [^js e]
                                      (case (.-key e)
                                        ("Enter" "Tab" "Escape") (.preventDefault e)
                                        nil)
                                      (let [direction (if (.-shiftKey e) ::dg/backwards ::dg/forwards)]
                                        (case (.-key e)
                                          "Enter"  (dg/focus-next-input ::dg/vertical direction dom/node)
                                          "Tab"    (dg/focus-next-input ::dg/horizontal direction dom/node)
                                          "Escape" (do (stage/discard!)
                                                       (set! (.-value dom/node) value)
                                                       (.focus (.closest dom/node "table")))
                                          nil))))
                 (dom/on! "input" (fn [^js e] (stage/stage! (.. e -target -value))))))))

(e/def schema-registry (reg/registry {}))
(e/def Render)

(e/defn DefaultRenderer [props e a V]
  (let [v (V.)]
    (e/client
      (dom/text (pr-str v)))))

(e/defn RenderBoolean [props e a V]
  (let [v (V.)]
    (e/client
      (let [stage! stage/stage!]
        (ui/checkbox v
            (e/fn* [v']
              (stage!
                [[::retract e a v] [::add e a v']])))))))

(e/defn RenderString [props e a V]
  (let [v (V.)]
    (e/client
      (let [stage! stage/stage!]
        (CellInput. v
          (e/fn* [v']
            (stage!
              [[::retract e a v]
               [::add e a v']])))))))

(e/defn RenderSeq [props e a V] ; basic impl, should work on ordered sets
  (let [v (V.)]
    (e/client
      (let [stage! stage/stage!]
        (CellInput. (str/join " " v)
          (e/fn* [v']
            (stage!
              [[::retract e a v]
               [::add e a (str/split v' #"\s+")]])))))))

(e/defn RenderCell [{::dom/keys [props]} e a V]
  (e/client
    (dg/cell {::dg/column a}
      (dom/props {:class "cell"})
      (dom/props props)
      (e/server (Render. {} e a V)))))

(e/defn JoinValue [v] (datafy v))
(e/defn Nav [v a] (nav v a (get v a)))

;; - TODO
;; - find how to pass props
;; - abstract over nav
;; - find the cell entity based on row

(e/defn DefaultRowRenderer [props e a V]
  (let [e vs/index ; TODO find a better entity
        v (JoinValue. (V.))]
    (e/client
      (dg/row
        (e/for-by identity [a (map ::dg/key dg/columns)]
          (e/server
            (RenderCell. {} e a (e/fn* [] (Nav. v a))))))))) ;; TODO generalize nav

(e/def RenderRow DefaultRowRenderer)

(defn clamp-to-upper-multiple
  "Returns the smallest multiple of `multiple` greater than or equal to `value`,
  or `value` itself if it's already a multiple of `multiple`."
  [value multiple]
  (if (zero? (mod value multiple))
    value
    (* (inc (quot value multiple)) multiple)))

(e/def !header-height-px) ; (atom 0)
(e/def header-height-px 0)
(e/def row-height 30)

(e/defn Header [{::keys [height-px]} Body]
  (e/client
    (let [height-px    (or height-px row-height)
          row-multiple (clamp-to-upper-multiple height-px row-height)]
      (reset! !header-height-px row-multiple)
      (dg/header
        (dg/row
          (dom/props {:style {:grid-row (str "1 / " (inc (quot row-multiple row-height)))}})
          (Body.))))))

(defmacro header [props & body]
  `(new Header ~props (e/fn* [] ~@body)))

(e/defn Column [{::keys [attribute title]} Body]
  (e/client
    (dg/column {::dg/key attribute}
      (dom/props {:style {:height (str header-height-px "px")}})
      (dom/text (or title (name attribute)))
      (Body.))))

(defmacro column [props & body]
  `(new Column ~props (e/fn* [] ~@body)))

(e/defn Grid [{::keys [row-height-px max-height-px rows RenderRow] ; WIP
               :or    {row-height-px 30
                       max-height-px 300
                       rows          []}}
              Body]
  (e/client
    (binding [!header-height-px (atom row-height-px)
              row-height        row-height-px]
      (binding [header-height-px (e/watch !header-height-px)]
        (vs/virtual-scroll {::vs/row-height-px  row-height-px
                            ::vs/max-height-px  max-height-px
                            ::vs/padding-top header-height-px
                            ::vs/rows-count  (e/server (count rows))}
          (dg/datagrid {::dg/row-height row-height-px}
            (dom/props {:tabIndex "1", :class [(styles/GridStyle.) (styles/CellsStyle.)]})
            (e/server
              (Body.)
              (vs/Paginate. rows RenderRow))))))))

(defmacro grid [props & body]
  `(new Grid ~props (e/fn* [] ~@body)))

(e/defn RenderGrid [{::keys [row-height-px max-height-px columns]
                     ::dom/keys [props]}
                    e a V]
  (e/server
    (grid {::row-height-px row-height-px
           ::max-height-px max-height-px
           ::rows          (V.)
           ::RenderRow     (e/fn* [row] (RenderRow. {} e a (e/fn* [] row)))}
      (e/client
        (dom/props props)
        (header {}
          (e/server
            (e/for-by ::key [{::keys [attribute title Body] ::dom/keys [props]} columns]
              (e/client
                (column {::attribute attribute
                         ::title     title}
                  (when props (dom/props props))
                  (e/server
                    (when Body (Body.))))))))))))

(e/def renderers {:string             RenderString
                  :boolean            RenderBoolean
                  [:sequential :one]  RenderSeq
                  [:sequential :many] RenderGrid})

(defn registry [m] (reg/registry (update-vals m malli/schema)))

(defn schema [registry a] (reg/schema registry a))
(defn schema-props [schema] (malli/properties schema))
(defn cardinality [schema] (:cardinality (schema-props schema) :one))
(defn schema-form [schema] (malli/form schema))
(defn schema-type [schema] (let [form (schema-form schema)]
                             (cond (vector? form) (first form)
                                   (map? form)    (:type form)
                                   :else          form)))



(comment
  (schema-type (malli/schema [:sequential {:foo :bar} :string]))
  (malli.util/equals (malli/schema [:sequential {:foo :bar} :string]) [:sequential :string]))


(defn resolve-renderer
  ([registry renderers a]
   (resolve-renderer registry renderers a nil))
  ([registry renderers a default-renderer]
   (if-let [schema (schema registry a)]
     (or
       (get renderers a)
       (get renderers [(schema-type schema) (cardinality schema)])
       (get renderers (schema-type schema))
       default-renderer)
     default-renderer)))

(e/defn SchemaRenderer [props e a V]
  (new (resolve-renderer schema-registry renderers a DefaultRenderer) props e a V))
