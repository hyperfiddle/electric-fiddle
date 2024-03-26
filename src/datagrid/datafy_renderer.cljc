(ns datagrid.datafy-renderer
  (:require
   [clojure.datafy :refer [datafy nav]]
   [contrib.data :as data]
   [datagrid.datagrid :as dg]
   [datagrid.stage :as stage]
   [datagrid.styles :as styles]
   [datagrid.virtual-scroll :as vs]
   [hyperfiddle.electric :as e]
   [hyperfiddle.electric-dom2 :as dom]
   [hyperfiddle.electric-css :as css]
   [hyperfiddle.electric-ui4 :as ui]
   [hyperfiddle.router :as router]
   [clojure.string :as str]
   [datagrid.schema :as schema])
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

(e/def schema-registry (schema/registry))
(e/def Render)

(e/defn JoinValue [v] (datafy v))
(e/defn Nav [v a] (nav v a (get v a)))

(e/def stack ())
(e/defn* PushEAV [e a V Continuation]
  (let [V (e/share (e/Comp. JoinValue V))]
    (binding [stack (conj stack [e a V])]
      (Continuation. e a V))))

(e/defn DefaultRenderer [props e a V]
  (let [v (JoinValue. (V.))]
    (e/client
      (dom/text (pr-str v)))))

(e/defn RenderBoolean [props e a V]
  (let [v (JoinValue. (V.))]
    (e/client
      (let [stage! stage/stage!]
        (ui/checkbox v
            (e/fn* [v']
              (stage!
                [[::retract e a v] [::add e a v']]))
          (set! (.-checked dom/node) v))))))

(e/defn RenderString [props e a V]
  (let [v (new (e/Comp. JoinValue V))]
    (e/client
      (dom/text v)
      #_(let [stage! stage/stage!]
        (CellInput. v
          (e/fn* [v']
            (stage!
              [[::retract e a v]
               [::add e a v']])))))))

(e/defn RenderSeq [props e a V] ; basic impl, should work on ordered sets
  (let [v (JoinValue. (V.))]
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

;; - TODO
;; - find how to pass props
;; - find the cell entity based on row

(e/defn DefaultRowRenderer [props e a V]
  (let [e vs/index ; TODO find a better entity
        v (JoinValue. (V.))]
    (e/client
      (dg/row
        (e/for-by identity [a (map ::dg/key dg/columns)]
          (e/server
            (PushEAV. e a (e/fn* [] (Nav. v a))
              (e/fn* [e a V]
                (RenderCell. {} e a V)))))))))

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

(e/defn Column [{::keys [attribute title sortable]} Body]
  (e/client
    (dg/column {::dg/key attribute
                ::dg/sortable sortable}
      (dom/props {:style {:height (str header-height-px "px")}
                  :title (str attribute " " (e/server (schema/schema-type (schema/schema schema-registry attribute))))
                  :data-title "my-title"})
      (dom/text (or title (name (or attribute ""))))
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

(e/def column-sort-spec "A map of column key to ::asc, ::desc, or nil." {})

(defn requalify-kw [ns-to-ns-map k]
  (if (qualified-keyword? k)
    (if-let [ns (get ns-to-ns-map (namespace k))]
      (data/qualify ns (data/unqualify k))
      k)
    k))

(defn get-ns [named] (when (ident? named) (namespace named)))

(e/defn RenderGrid [{::keys [row-height-px max-height-px columns]
                     ::dom/keys [props]}
                    e a V]
  (e/client
    (dg/SortController.
      (e/fn* []
        (binding [column-sort-spec (update-vals dg/column-sort-spec (partial requalify-kw {(get-ns ::dg/_) (get-ns ::_)}))]
          (e/server
            (grid {::row-height-px row-height-px
                   ::max-height-px max-height-px
                   ::rows          (V.)
                   ::RenderRow     (e/fn* [row]
                                     (PushEAV. e a (e/fn* [] row)
                                       (e/fn* [e a V] (RenderRow. {} e a V))))}
              (e/client
                (dom/props props)
                (header {}
                  (e/server
                    (e/for-by ::key [{::keys [attribute title sortable Body] ::dom/keys [props]} columns]
                      (e/client
                        (column {::attribute attribute
                                 ::title     title
                                 ::sortable  sortable}
                          (when props (dom/props props))
                          (e/server
                            (when Body (Body.))))))))))))))))

(e/def FormStyle
  (e/client
    (e/share (e/fn* []
               (css/scoped-style
                 (css/rule {:display :grid
                            :grid-template-columns "auto 1fr"
                            :gap "0 0.5rem"}))))))

(e/defn Sequence [coll]
  (->> (seq coll)
    (map (fn [[k v]] {::key k, ::value v}))))

(defn pad [rank]
  (str/join "" (repeat rank "    "))) ; Non-breaking space

(e/def RenderKey DefaultRenderer)

(e/defn RenderFormKey [props e a V]
  (e/server
    (let [[_ [_e _a V⁻¹]] stack
          row (V⁻¹.)
          rank (::rank row 0)
          V (e/Comp. JoinValue V)
          v (V.)]
      (e/client
        (dom/label
          (dom/props {:title (str v
                               " " (e/server (schema/schema-type (schema/schema schema-registry v)))
                               " " (e/server (schema/cardinality (schema/schema schema-registry v))))})

          (dom/text (pad rank))
          (e/server
            (RenderKey. props e a V)))))))

(e/defn RenderForm [props e a V]
  ;; TODO link label with ::value column through :for attribute
  (RenderGrid. (-> props (update ::dom/props assoc :role "form")
                 (update-in [::dom/props :style] assoc :grid-template-columns "auto 1fr")
                 (assoc ::columns [{::attribute ::key}
                                   {::attribute ::value}]))
    e a (e/fn* []
          (Sequence. (JoinValue. (V.))))))

(e/def renderers {:string                     RenderString
                  :boolean                    RenderBoolean
                  ;; [:sequential ::schema/one]  RenderSeq
                  ;; [:sequential ::schema/many] RenderGrid
                  ::schema/many               RenderGrid
                  ::key                       RenderFormKey})

(defn get-first "like `clojure.core/get` but return the first key found in map `m`"
  [m keys]
  (first (remove nil? (map #(get m %) keys))))

(defn resolve-renderer
  ([registry renderers a]
   (resolve-renderer registry renderers a nil))
  ([registry renderers a default-renderer]
   (or
     (get renderers a)
     (when-let [schema (schema/schema registry a)]
       (or (get renderers [(schema/schema-type schema) (schema/cardinality schema)])
           (get-first renderers (schema/schema-types schema)) ; try with type at point
           #_(get renderers (schema/resolve-type schema)) ; try with primitive type
           #_(get renderers (schema/cardinality schema)) ; not sure if it make sense?
           ))
     default-renderer)))

(e/defn SchemaRenderer [props e a V]
  (new (resolve-renderer schema-registry renderers a DefaultRenderer) props e a V))

;; inputs

(e/defn InputValue [attribute]
  (e/client (not-empty (ffirst (get router/route attribute)))))

(e/defn RouterStorage [attribute Body]
  (when-let [value' (Body. (InputValue. attribute))]
    (router/ReplaceState!. ['. {attribute value'}])
    value'))

(e/defn RenderInput [props attribute value]
  (e/client
    (dom/input
      (dom/props {:placeholder (::dom/placeholder props (str attribute))
                  :style {:border "1px gray solid"}})
      (dom/props (dissoc (contrib.data/select-ns 'hyperfiddle.electric-dom2 props) ::dom/placeholder))
      (set! (.-value dom/node) value)
      (str (dom/on! "input" (fn [^js e] (.. e -target -value)))))))

(defn needle-match
  "Case insensitive check if `needle-str` is included in `str`.
  TODO ignore diacritics and variants (normalize and collate), or let the database/fts engine handle it."
  [needle-str str]
  (str/includes? (str/lower-case (or str "")) (str/lower-case (or needle-str ""))))

(e/defn InputFilter
  ([attribute coll] (InputFilter. attribute attribute coll))
  ([keyfn attribute coll] (InputFilter. needle-match keyfn attribute coll))
  ([sort-fn keyfn attribute coll]
   (if-let [input-value (InputValue. attribute)]
     (filter #(sort-fn input-value (keyfn %)) coll)
     coll)))
