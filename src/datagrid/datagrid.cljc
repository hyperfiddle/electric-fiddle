(ns datagrid.datagrid
  (:require [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]
            [clojure.string :as str]
            [datagrid.virtual-scroll :as vs]
            [missionary.core :as m]
            [contrib.missionary-contrib :as mx])
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

(defn make-column [node frozen?]
  {::id        (gensym "column_")
   ::node      node
   ::width     0
   ::position  0
   ::frozen?   frozen?})

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
      ::auto   (str "minmax(min-content, " px ")")
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

(e/defn* DataGrid [{::keys [row-height]} Body]
  (e/client
    (let [!loading? (atom false)]
      (binding [loading? (e/watch !loading?)
                id (str (gensym "datagrid_"))
                !columns-index (atom {})
                !sizing-mode (atom ::auto)
                datagrid.datagrid/row-height row-height]
        (binding [columns-index (e/watch !columns-index)
                  sizing-mode (e/watch !sizing-mode)]
          (binding [columns (ordered-columns (vals columns-index))]
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
                                  :grid-template-columns (grid-template-columns sizing-mode columns)
                                  :grid-auto-rows        (str row-height "px")}})
              ;; @media print{
              ;;  .hyperfiddle.subgrid-row
              ;;  , .hyperfiddle .datagrid tr {
              ;;    break-inside: avoid;
              ;;  }
              ;; }

              ;; TODO hook on virtualscroll scroll state to freeze column sizes
              ;; (dom/on! dom/node "wheel" #(reset! !sizing-mode ::manual))
              (dom/element :style
                (dom/text
                  #_(str/join "\n" (map (fn [{::keys [position width]}]
                                          (str "#" id " td:nth-child(" position "){ max-width:" (width-px width "none") ";}"))
                                     columns-index))
                  (str "#"id " th {z-index: 2;}")
                  (str/join "\n" (map (fn [{::keys [position] :as column}]
                                        (str "#" id " th:nth-child(" position ")"
                                          "{position: sticky; left:" (column-offset columns column) "px; z-index:3;}"
                                          "#" id " td:nth-child(" position ")"
                                          "{position: sticky; left:" (column-offset columns column) "px; z-index:2;}"))
                                   (filter ::frozen? columns)
                                   ))))
              (try (Body.)
                   (catch Pending p
                     (reset! !loading? true)
                     (e/on-unmount #(reset! !loading? false))
                     (throw p))))))))))

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
                                                              (! (Math/ceil (if-let [cbs (.-borderBoxSize e)]
                                                                               (if-let [dimentions (aget cbs 0)]
                                                                                 (.-inlineSize dimentions)
                                                                                 (.-inlineSize cbs))
                                                                               (.. e -contentRect -width)))))))]
                      (.observe observer node)
                      #(.unobserve observer node)))))))



(e/defn* Column [{::keys [frozen?]} Body]
  (e/client
    (dom/th
      (let [{::keys [id] :as column} (make-column dom/node frozen?)]
        (dom/props {:style {:position      :sticky
                            :top           0
                            :display :block
                            :overflow      :hidden
                            :text-overflow :ellipsis
                            :white-space   :nowrap}})
        (ColumnStateMachine. column)
        (swap! !columns-index (fn [columns-index]
                                (-> (assoc columns-index id column)
                                  (update-vals update-column))))
        (swap! !columns-index update id assoc ::width (new (column-size column)))
        (e/on-unmount #(swap! !columns-index dissoc id))
        (Body.)))))

(defmacro column [props & body]
  `(new Column ~props (e/fn* [] ~@body)))

#?(:cljs
   (let [supported? (.supports js/CSS "grid-template-columns:subgrid")]
     (defn subgrid? [then else]
       (if supported? then else))))

(e/defn* Row [Body]
  (e/client
    (dom/tr
      (dom/props {:style (subgrid?
                           {:display :grid
                            :grid-template-columns :subgrid
                            :grid-column "1 / -1"
                            :transform "scale(1)"}
                           {:display :contents})})
      (Body.))))

(defmacro row [& body]
  `(new Row (e/fn* [] ~@body)))

(e/defn* Cell [Body]
  (e/client
    (dom/td
      (dom/props {:style {:padding 0, :margin 0}})
      (when row-height ; TODO delegate to CSS engine by setting a CSS variable instead
        (dom/props {:style {:height (str row-height "px")}}))
      (Body.))))

(defmacro cell [& body]
  `(new Cell (e/fn* [] ~@body)))

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
