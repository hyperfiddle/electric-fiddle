(ns datagrid.virtual-scroll
  (:require [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]
            [hyperfiddle.electric-ui4 :as ui]
            [missionary.core :as m]
            #?(:cljs goog.style))
  (:import [hyperfiddle.electric Pending]))

#?(:cljs
   (defn resize-observer [node]
     (->> (m/observe (fn [!]
                       (! [(.-clientWidth node) (.-clientHeight node)])
                       (let [obs (new js/ResizeObserver (fn [entries]
                                                          (let [content-box-size (-> entries (aget 0) .-contentBoxSize (aget 0))]
                                                            (! [(.-inlineSize content-box-size) (.-blockSize content-box-size)]))))]
                         (.observe obs node)
                         #(.unobserve obs))))
       (m/relieve {}))))

(e/defn* ResizeObserver
  "Subscribe to [inlineSize blockSize] for the given `dom-node`."
  [dom-node]
  (e/client (new (resize-observer dom-node))))

;; ---

(defn slice
  "Return a subset of `coll`, starting at `start` and stopping at `start`+`width`.
  Similar to `clojure.core/subvec`. Uses `subvec` is `coll` is a vector.
  Otherwise uses `take` and `drop`."
  [coll start width]
  (if (vector? coll)
    (subvec coll (min (max 0 start) (count coll)) (min (+ start width) (count coll))) ; O(1)
    (take width (drop start coll))                                 ; O(n)
    ))

(defn window
  "Computes a window over a paginated scroll view. I.e. computes which rows should
  be visible on screen and how tall should the scrollable element be, depending on:
  - scrollbar position
  - top padding of the scrollable container
  - total number of rows
  - height of one row (it is assumed all rows have the same height)
  - height of the scrollable container.
  Return a triple:
  - scrollable height: total height the container would be if it was not scrollable.
  - first row index: index of the first row that should be visible to the user
  - mounted rows count: how many rows should be visible on screen.
  "
  [row-height padding-top rows-count scrollTop clientHeight]
  (let [scrollable-height  (+ (* rows-count row-height) padding-top)
        first-row-index    (max 0 (Math/floor (/ scrollTop row-height)))
        mounted-rows-count (max 0 (min (- rows-count first-row-index) (Math/ceil (/ (- clientHeight padding-top) row-height))))]
    [scrollable-height first-row-index mounted-rows-count]))

(e/def rows-count 0)
(e/def row-height 30)
(e/def first-row-index 0)
(e/def mounted-rows-count 0)
(e/def !scroll-watchers nil) ; (atom #{})

(e/defn* RegisterScrollWatch
  "Attach a callback to the current virtual scroll instance, so child components
  can become notified when their container is scrolled. Example use case: a
  excel-like datagrid will autosize its columns based on the initial content so
  to produce a nice default layout. But colums should become fixed width as soon
  as the user starts scrolling. Otherwise columns width would adjust to scrolled
  rows content."
  [callback]
  (when (some? !scroll-watchers)
    (swap! !scroll-watchers conj callback)
    (e/on-unmount #(swap! !scroll-watchers disj callback))))

(defn notify-scroll-watches [scroll-watchers]
  (doseq [watcher scroll-watchers]
    (watcher)))

(defn scrollbar-width "Computes the width the current browser scrollbar" []  #?(:clj 0, :cljs (goog.style/getScrollbarWidth)))

(e/def scrollbar-thinkness (scrollbar-width))

(defmacro virtual-scroll
  "Paginate over a collection of rows. Pagination can happen client side or server
  side. Will compute a window depending on the container height (set with CSS
  height and max-height) and the current container’s scrollbar position.

  Takes:
  - `rows-count`: total number of rows to paginate over - pagination over an infinite sequence is not supported.
  - `row-height`: individual row height, in px. - all rows are assumed to be the same height. Dynamic row height is not supported.
  - `padding-top`: containers’s top padding in px, to offset the scroll position - usually for rendering a table header in place of the first row(s).
  - `body` arbitrary electric code, usually a dom/table or dom/ul into which one need to call `Paginate`.
  "
  [{::keys [rows-count row-height padding-top] :or {padding-top 0}} & body]
  `(e/client
     (binding [rows-count (or ~rows-count 0)
               row-height (or ~row-height 30)
               !scroll-watchers (atom #{})]
       (dom/div (dom/props {:class "virtual-scroll"
                            :style {:overflow :auto
                                    :position :relative
                                    ;; --virtual-scroll-row-height: 30px; TODO migrate
                                    }})
                (dom/element "style"
                  (dom/text
                    "
@media print{
  ::-webkit-scrollbar{
    display: none;
  }
  .virtual-scroll{
    height: max-content!important; /* Expand virtual scrolls for print. Make sure you set a max-height. */
    overflow-y: visible;
  }
}
.virtual-scroll > *:not([aria-hidden=\"true\"]) {
  position: sticky;
  top:0;
}
"))
                (dom/on! "scroll" (partial notify-scroll-watches (e/watch !scroll-watchers)))
                (let [[scrollTop# ~'_ ~'_] (new (ui/scroll-state< dom/node))
                      clientHeight#        (second (new ResizeObserver dom/node))
                      [scrollable-height# first-row-index# mounted-rows-count#]
                      (window row-height (or ~padding-top 0) rows-count (js/Math.floor scrollTop#) clientHeight#)
                      scrollbar-padding# (if (inc (> scrollable-height# clientHeight#)) scrollbar-thinkness 0)]
                  (dom/props {:style {:height                      (str (+ scrollable-height# scrollbar-padding#) "px")
                                      :--virtual-scroll-row-height (str row-height "px")}})
                  (dom/div (dom/props {:style {:position :absolute,:min-width "1px", :height (str scrollable-height# "px") :visibility :hidden}
                                       :aria-hidden true}))
                  (binding [first-row-index    first-row-index#
                            mounted-rows-count mounted-rows-count#]
                    ~@body))))))

(e/def index 0)
(e/def row-number 0)

(e/defn Paginate [rows RenderRow]
  (let [rows-index (zipmap (range) (slice rows (e/client first-row-index) (e/client mounted-rows-count)))]
    (e/for [idx (sort (keys rows-index))]
      (binding [index      idx
                row-number (+ first-row-index idx)]
        (RenderRow. (get rows-index idx))))))

;; ; example: paginated picker options list, window of 10 rows. Each row is 30px tall.
;; (e/server
;;   (let [num-rows (count rows)] ; rows is a large collection
;;     (e/client
;;       (vs/virtual-scroll {::vs/rows-count num-rows ; only transfer row count, not all rows
;;                           ::vs/row-height 30}
;;         (dom/props {:style {:min-height "30px" :height (str (* 30 (min 10 num-rows)))}})
;;         (dg/datagrid {::dg/row-height 30} ; renders a dom/table on steroids
;;           (e/server
;;             (vs/Paginate. rows ; pagination happens on the server because of e/server above
;;               (e/fn [{:keys [id value]}] ; render one row
;;                 (e/client
;;                   (dg/row ; datagrid row UI component, usually a dom/tr
;;                     (p/option {:as dg/cell :value {:id id, :name value}} ; a dom/td on steroids
;;                       (dom/text value))))))))))))

