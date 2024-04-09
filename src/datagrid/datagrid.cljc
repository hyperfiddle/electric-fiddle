(ns datagrid.datagrid
  (:require [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]
            #?(:cljs [hyperfiddle.electric-dom2.batch :as batch])
            [clojure.string :as str]
            [datagrid.virtual-scroll :as vs]
            [missionary.core :as m]
            [contrib.missionary-contrib :as mx]
            [hyperfiddle.electric-css :as css])
  #?(:cljs (:require-macros [datagrid.datagrid]))
  (:import [hyperfiddle.electric Pending]))

(e/def id)
(e/def !columns-index)
(e/def columns-index {})
(e/def columns (list))
(e/def !sizing-mode)
(e/def sizing-mode ::auto)
(e/def row-height)
(e/def loading?)

;;; Column model

(defn make-column
  ([node frozen?] (make-column node frozen? nil))
  ([node frozen? extra-kvs]
   (merge {::id        (gensym "column_")
           ::node      node
           ::width     0
           ::position  0
           ::frozen?   frozen?}
     extra-kvs)))

(defn column-range [columns start end]
  (->> columns
    (sort-by ::position)
    (drop-while #(< (::position %) start))
    (take-while #(< (::position %) end))))

(defn column-offset [columns column]
  (reduce + (map #(or (::width %) 0) (column-range columns 0 (::position column)))))

(defn column-position [{::keys [node]}]
  (loop [position 0
         node node]
    (if (nil? node)
      position
      (recur (inc position) (.-previousElementSibling node)))))

(defn update-column [column]
  (-> column
    (assoc ::position (column-position column))
    ))

(defn ordered-columns [columns] (sort-by ::position columns))

;;; Resize logic

(e/def MouseDown? "true when mouse pointer is down on `node`, false otherwise"
  (e/fn* [node]
    (e/client
      (->> (mx/mix
             (e/listen> node "mousedown" (constantly true))
             (e/listen> node "mouseup" (constantly false)))
        (m/reductions {} false)
        (m/relieve {})
        new))))

(e/def ColumnStateMachine
  (e/fn* [{::keys [id node] :as column}]
    (e/client
      (when (MouseDown?. node)
        (when (dom/on! node "mousemove" (constantly true))
          (reset! !sizing-mode ::manual))))))

(defn grid-template-column [sizing-mode column]
  (let [px (str (::width column) "px")]
    (case sizing-mode
      ::auto   "auto" #_(str "minmax(min-content, " px ")")
      ::manual px)))

(defn grid-template-columns [sizing-mode columns]
  (str/join " " (map (partial grid-template-column sizing-mode) (ordered-columns columns))))

;; (defn width-px [px default]
;;   (if px (str px "px") default))

#?(:cljs
   (defn blur-focused-element [node]
     (when-let [^js activeElement (.-activeElement js/document)]
       (when (.contains node activeElement)
         (.blur activeElement)))))

(e/defn* GridStyle [id columns]
  (e/client
   (css/style ;; TODO move to scoped style?
    (css/rule ".datagrid_datagrid__row"
              {:display               :grid
               :grid-template-columns :subgrid
               :grid-column           "1 / -1"
               :transform             "scale(1)"
               :z-index               1})
    (css/rule ".datagrid_datagrid__column"
              {:position      :sticky
               :top           0
               :display       :block
               :box-sizing    :border-box
               :padding-left  "1px"
               :padding-right "1px"
               :overflow      :hidden
               :text-overflow :ellipsis
               :white-space   :nowrap})
    (css/rule (str "#" id " thead > tr > th") {:z-index 2})
    (css/rule (str "#" id " > tr > td")
      {#_#_:padding    0
       #_#_:margin     0
       :box-sizing :border-box
       :height     "var(--row-height)"})
    (e/for [{::keys [position] :as column} (filter ::frozen? columns)]
      (css/rule (str "#" id " th:nth-child(" position ")") {:postition :sticky
                                                            :left      (str (column-offset columns column) "px")
                                                            :z-index   3})
      (css/rule (str "#" id " td:nth-child(" position ")") {:postition :sticky
                                                            :left      (str (column-offset columns column) "px")
                                                            :z-index   2})))))

(e/def columns-key-index)

(defn cell-position [columns-key-index column-key] (get-in columns-key-index [column-key ::position] 0))

(e/def toggle-column-sort-spec! (constantly nil))
(e/def column-sort-spec "A map of column key to either ::asc, ::desc, or nil" {})

(defn toggle-sort [!atom column-key]
  (swap! !atom (fn [atom] (-> (select-keys atom [column-key])
                              (update column-key #(case % ::asc ::desc (::desc nil) ::asc))))))

(e/defn SortController [Body]
  (let [!column-sort-spec (atom {})]
    (binding [toggle-column-sort-spec! (partial toggle-sort !column-sort-spec)
              column-sort-spec (e/watch !column-sort-spec)]
      (Body.))))

(defn get-template-columns [props]
  (or (get-in props [::dom/style :grid-template-columns])
    (get-in props [::dom/style "grid-template-columns"])
    (get-in props [:style :grid-template-columns])
    (get-in props [:style "grid-template-columns"])))

(e/defn* DataGrid [{::keys [row-height] ::dom/keys [props]} Body]
  (let [template-columns (get-template-columns props)]
    (e/client
      (let [!loading? (atom false)]
        (binding [loading? (e/watch !loading?)
                  id (str (gensym "datagrid_"))
                  !columns-index (atom {})
                  !sizing-mode (atom ::auto)
                  datagrid.datagrid/row-height row-height]
          (binding [columns-index (e/watch !columns-index)
                    sizing-mode (e/watch !sizing-mode)]
            (binding [columns (ordered-columns (vals columns-index))
                      columns-key-index (into {} (map (juxt ::key identity) (filter ::key (vals columns-index))))]
              (dom/table
                (vs/RegisterScrollWatch. #(do (reset! !sizing-mode ::manual)
                                              (blur-focused-element dom/node)))
                (dom/on! js/window "beforeprint" #(reset! !sizing-mode ::manual))
                (dom/props {:id id
                            :tabIndex 0 ; ensure this is focusable for use in pickers
                            :class "datagrid",
                            :style {:display               :grid
                                    :align-content         :flex-start
                                    :font-variant-numeric  "tabular-nums"
                                    :grid-template-columns (or template-columns (grid-template-columns sizing-mode columns))
                                    :--row-height          (str row-height "px")
                                    :grid-auto-rows        (str row-height "px")}})
                (GridStyle. id columns)
                ;; @media print{
                ;;  .hyperfiddle.subgrid-row
                ;;  , .hyperfiddle .datagrid tr {
                ;;    break-inside: avoid;
                ;;  }
                ;; }

                ;; TODO hook on virtualscroll scroll state to freeze column sizes
                ;; (dom/on! dom/node "wheel" #(reset! !sizing-mode ::manual))
                (try (Body.)
                     (catch Pending p
                       (reset! !loading? true)
                       (e/on-unmount #(reset! !loading? false))
                       (throw p)))))))))))

(defmacro datagrid [props & body]
  `(new DataGrid ~props (e/fn* [] ~@body)))

(e/defn* Header [Body]
  (e/client
   (dom/thead
    (dom/props {:style {:display :contents}})
    (Body.))))

(defmacro header [& body]
  `(new Header (e/fn* [] ~@body)))

#?(:cljs
   (defn column-size [{::keys [node]}]
     (m/relieve {}
       (m/observe (fn [!]
                    (! nil)
                    (let [observer (new js/ResizeObserver (fn [entries]
                                                            (doseq [e entries]
                                                              (! (.toFixed (if-let [cbs (.-borderBoxSize e)]
                                                                             (if-let [dimentions (aget cbs 0)]
                                                                               (.-inlineSize dimentions)
                                                                               (.-inlineSize cbs))
                                                                             (.. e -contentRect -width))
                                                                   2)))))]
                      (.observe observer node)
                      #(.unobserve observer node)))))))

(e/defn* Column [{::keys [frozen? key sortable]} Body]
  (e/client
    (dom/th
      (let [{::keys [id] :as column} (make-column dom/node frozen? {::key key})]
        (dom/props {:class "datagrid_datagrid__column"})
        (ColumnStateMachine. column)
        (batch/schedule!
          (fn [] ; measure column postion after column is inserted in the DOM
            (swap! !columns-index (fn [columns-index]
                                    (-> (assoc columns-index id column)
                                      (update-vals update-column))))))
        (swap! !columns-index update id assoc ::width (new (column-size column)))
        (e/on-unmount #(swap! !columns-index dissoc id))
        (let [b (Body.)]
          (when sortable ;; TODO move this bit of UI out of Column, make it part of Body, not a prop
            (dom/button (dom/props {:style {:position :absolute
                                            :right "1px"}})
              (dom/text (case (get column-sort-spec key)
                          nil "-"
                          ::asc "^"
                          ::desc "v"))
              (dom/on! "click" #(toggle-column-sort-spec! key))))
          b)))))

(defmacro column [props & body]
  `(new Column ~props (e/fn* [] ~@body)))

#?(:cljs
   (let [supported? (.supports js/CSS "grid-template-columns:subgrid")]
     (defn subgrid?
       ([] supported?)
       ([then else] ; to save on an electric conditional
        (if supported? then else)))))

(e/defn* Row [Body]
  (e/client
   (dom/tr
     (dom/props (subgrid? {:class "datagrid_datagrid__row"} {:style {:display :contents}}))
     (dom/props {:style {:z-index (- (max vs/index (e/server vs/index)))}}) ; FIXME binding unification
     (Body.))))

(defmacro row [& body]
  `(new Row (e/fn* [] ~@body)))

(e/defn* Cell [{::keys [column]} Body]
  (e/client
   (dom/td
     (dom/props {:style {:grid-column (cell-position columns-key-index column)}})
     (Body.))))

(defmacro cell [props & body]
  `(new Cell ~props (e/fn* [] ~@body)))

(comment
  (e/defn Test []
    (let [rows (range 20)]
      (vs/virtual-scroll {::vs/rows-count  (count rows)
                          ::vs/row-height  30
                          ::vs/padding-top 30}
        (datagrid {::row-height 30}
          (dom/props {:style {:width  "100%"
                              :height (str (* 30 10) "px")}})
          (dom/element :style (dom/text "table.datagrid td { border-top: 1px lightgray solid;
                                          border-left:1px lightgray solid;
                                          box-sizing: border-box; }"))
          (header
            (row
              (column (dom/props {:style {:height "30px"}}) (dom/text "A"))
              (column (dom/text "B"))
              (column (dom/text "C"))))
          (vs/ClientSidePagination. rows
            (e/fn* [i]
              (row
                (cell [] (dom/text "aaaa" i))
                (cell [] (dom/text "b" i))
                (cell [] (dom/text "c" i))))))))))

(defn find-input-horizontal [direction node]
  (let [step (case direction
               ::forwards  #(.-nextElementSibling %)
               ::backwards #(.-previousElementSibling %))
        cell (.closest node "td")]
    (if-let [next-input (loop [next-cell (step cell)]
                          (when next-cell
                            (if-let [next-input (.querySelector next-cell "input")]
                              next-input
                              (recur (step next-cell)))))]
      [::same-line next-input]
      (let [row (.closest cell "tr")]
        (loop [next-row (step row)]
          (when next-row
            (if-let [next-input (.querySelector next-row "input")]
              [::other-line next-input]
              (recur (step next-row)))))))))

(defn find-input-vertical [direction node]
  (let [cell    (.closest node "td")
        row     (.closest node "tr")
        y-index (max 0 (dec (count (take-while some? (iterate #(.-previousElementSibling %) cell)))))]
    (when-let [next-row (case direction
                          ::forwards  (.-nextElementSibling row)
                          ::backwards (.-previousElementSibling row))]
      (let [next-row-cells-count (.-childElementCount next-row)
            next-cell            (aget (.-childNodes next-row) (min next-row-cells-count y-index))]
        (when-let [next-input (.querySelector next-cell "input")]
          [::other-line next-input])))))

(defn find-next-input [axis direction node]
  (case axis
    ::horizontal (find-input-horizontal direction node)
    ::vertical   (find-input-vertical direction node)))

(def flip-axis {::horizontal ::vertical
                ::vertical   ::horizontal})

#?(:cljs
   (defn focus-next-input [axis direction node]
     (if-let [found (find-next-input axis direction node)]
       (let [[location next-input] found]
         (when (= ::other-line location)
           (set! (.-scrollLeft (.closest node ".virtual-scroll")) 0))
         (.scrollIntoView next-input #js{:block "nearest", :inline "nearest"})
         (.setTimeout js/window #(.focus next-input) 50))
       (if (find-next-input (flip-axis axis) direction node)
         (focus-next-input (flip-axis axis) direction node)
         (.blur node)))))

;; How to do Undo/redo with a CollectionEditor
#_(dom/on "keydown" (e/fn* [^js e] ; undo/redo (Ctrl-z / Shift-Ctrl-z)
                      (when (and (or (.-ctrlKey e) (.-metaKey e)) (= "z" (.-key e)))
                        (if (.-shiftKey e)
                          (redo!)
                          (undo!)))))

;; How to do rows drag-n-drop
;; Usually implemented on the row index column (the leftmost one)
;; Row numbers usually are the handle.
#_(dom/on! "contextmenu" (fn [event] (cm/open! {:row-number row-number} event)))
#_(dom/on! "dragstart" (fn [^js e]
                         (set! (.. e -dataTransfer -dropEffect) "move")
                         (.. e -dataTransfer (setDragImage (.. e -target (closest "tr")) 0 0))
                         (.. e -dataTransfer (setData "text/plain" row-number))))
#_(dom/on! "dragover" (fn [^js e] (.preventDefault e))) ; recommended by MDN
#_(dom/on "drop" (e/fn* [^js e] (let [from (js/parseInt (.. e -dataTransfer (getData "text/plain")))]
                                  (e/server (rotate! from row-number)))))
