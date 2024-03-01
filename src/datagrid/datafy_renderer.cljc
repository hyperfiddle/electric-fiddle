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
   [hyperfiddle.electric-ui4 :as ui])
  (:import
   (hyperfiddle.electric Pending))
  #?(:cljs (:require-macros datagrid.datafy-renderer)))


(e/def loading? false)

(e/defn RowChangeMonitor [{::keys [rows OnChange]} Body]
  (e/client
    (let [!loading? (atom false)]
      (binding [loading? (e/watch !loading?)]
        (e/server
          (let [output-rows (Body. rows)]
            (when (not= rows output-rows)
              (try (OnChange. output-rows)
                   nil
                   (catch Pending _
                     (e/client
                       (reset! !loading? true)
                       (e/on-unmount #(reset! !loading? false))))))))))))

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

(e/def set-row!)

(e/defn RenderBoolean [idx key value]
  (e/client
    (ui/checkbox value (e/fn* [value']
                         (prn idx key value')))))

(e/defn RenderString [idx key value]
  (e/client
    (CellInput. value
      (e/fn* [value']
        (prn idx key value')))))

(e/defn RenderValue [e a v]
  (let [v (datafy v)]
    (cond (string? v)  (RenderString. e a v)
          (boolean? v) (RenderBoolean. e a v)
          (nil? v)     nil
          :else        (e/client (dom/text (pr-str v))))))

(e/defn Cell [props e a v]
  (e/client
    (dg/cell {::dg/column a}
      (dom/props {:class "cell"})
      (dom/props props)
      (try
        (e/server (RenderValue. e a v))
        (catch Pending _
          #_(prn "pending 2"))))))

(e/defn Row [row]
  (let [e vs/index  ; virtual scroll row index is the default row identifier
        data (datafy row)]
    (e/client
      (dg/row
        (try
          (e/for-by identity [a (map ::dg/key dg/columns)]
            (e/server
              (Cell. {} e a (nav data a (get data a)))))
          (catch Pending _
            #_(prn "pending 3")))))))

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

(e/defn Column [{::keys [key]} Body]
  (e/client
    (dg/column {::dg/key key}
      (dom/props {:style {:height (str header-height-px "px")}})
      (Body.))))

(defmacro column [props & body]
  `(new Column ~props (e/fn* [] ~@body)))

(e/defn Grid [{::keys [row-height-px max-height-px OnChange RenderRow rows] ; WIP
               :or    {row-height-px 30
                       max-height-px 300
                       rows          []}}
              Body]
  (e/server
    (RowChangeMonitor. {::rows rows, ::OnChange (or OnChange (e/fn* [_]))}
      (e/fn* [rows]
        (let [{::ce/keys [rows change!]} (ce/CollectionEditor. (vec rows))]
          (binding [set-row! change!]
            (try
              (e/client
                (binding [!header-height-px (atom row-height-px)
                          row-height        row-height-px]
                  (binding [header-height-px (e/watch !header-height-px)]
                    (vs/virtual-scroll {::vs/row-height  row-height-px
                                        ::vs/padding-top header-height-px
                                        ::vs/rows-count  (e/server (count rows))}
                      (dom/props {:style {:max-height (str max-height-px "px")}}) ; total max-height is 1 header
                      (dg/datagrid {::dg/row-height row-height-px}
                        (dom/props {:tabIndex "1", :class [(styles/GridStyle.) (styles/CellsStyle.)]})
                        (e/server (Body.))
                        (e/server (vs/Paginate. rows (or RenderRow Row))))))))
              (catch Pending _))))))))

(defmacro grid [props & body]
  `(new Grid ~props (e/fn* [] ~@body)))
