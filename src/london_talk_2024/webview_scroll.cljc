(ns london-talk-2024.webview-scroll
  (:require [contrib.data :refer [unqualify]]
            #?(:clj [datascript.core :as d])
            #?(:cljs goog.object)
            [hyperfiddle.electric-de :as e :refer [$]]
            [hyperfiddle.electric-dom3 :as dom]
            [missionary.core :as m]
            #?(:clj [models.teeshirt-orders-datascript-dustin :refer [conn teeshirt-orders genders shirt-sizes]])))

(e/defn Tap-diffs [tap! x] (doto x (-> e/pure e/input tap!)))

(defn throttle [dur >in]
  (m/ap
    (let [x (m/?> (m/relieve {} >in))]
      (m/amb x (do (m/? (m/sleep dur)) (m/amb))))))

#?(:cljs (defn sample-scroll-state! [scrollable]
           [(.. scrollable -scrollTop) ; optimization - detect changes (pointless)
            (.. scrollable -scrollHeight) ; snapshot height to detect layout shifts in flipped mode
            (.. scrollable -clientHeight)])) ; measured viewport height (scrollbar length)

#?(:cljs (defn scroll-state> [scrollable]
           (m/observe
             (fn [!]
               (let [sample (fn [] (! (sample-scroll-state! scrollable)))]
                 (.addEventListener scrollable "scroll" sample #js {"passive" true})
                 #(.removeEventListener scrollable "scroll" sample))))))

#?(:cljs (def !scrollStateDebug (atom nil)))

#?(:cljs (defn scroll-state< [scrollable]
           (->> (scroll-state> scrollable)
             (throttle 16) ; RAF interval
             (m/reductions {} [0 0 0])
             (m/relieve {})
             (m/latest (fn [[scrollTop scrollHeight clientHeight :as s]]
                         (reset! !scrollStateDebug {::scrollTop scrollTop
                                                    ::scrollHeight scrollHeight
                                                    ::clientHeight clientHeight})
                         s)))))

#?(:cljs (defn resize-observer [node]
           (->> (m/observe (fn [!]
                             (! [(.-clientWidth node) (.-clientHeight node)])
                             (let [obs (new js/ResizeObserver (fn [entries]
                                                                (let [content-box-size (-> entries (aget 0) .-contentBoxSize (aget 0))]
                                                                  (! [(.-inlineSize content-box-size) (.-blockSize content-box-size)]))))]
                               (.observe obs node)
                               #(.unobserve obs))))
             (m/relieve {}))))

(e/defn ResizeObserver
  "Subscribe to [inlineSize blockSize] for the given `dom-node`."
  [dom-node]
  (e/client (e/input (resize-observer dom-node))))

(e/defn SearchInput []
  (dom/input (dom/props {:placeholder "Filter..."})
    ($ dom/On "input" #(-> % .-target .-value))))

(e/defn Typeahead [v-id Options & [OptionLabel]]
  (e/client
    (dom/div (dom/props {:class "hyperfiddle-typeahead"})
      (let [OptionLabel (or OptionLabel (e/fn [x] (pr-str x)))
            container dom/node
            !v-id (atom v-id) v-id (e/watch !v-id)]
        (dom/input
          (dom/props {:placeholder "Filter..."})
          (if-some [close! ($ e/Token ($ dom/On "focus"))]
            (let [search ($ dom/On "input" #(-> % .-target .-value))]
              (binding [dom/node container] ; portal
                (dom/ul
                  (e/cursor [id ($ Options search)]
                    (dom/li (dom/text ($ OptionLabel id))
                      ($ dom/On "click" (fn [e]
                                          (doto e (.stopPropagation) (.preventDefault))
                                          (reset! !v-id id) (close!))))))))
            (dom/props {:value ($ OptionLabel v-id)}))) ; controlled only when not focused
        v-id))))

(defn window [xs offset limit]
  (subvec (vec xs) ; fast cast
    (Math/max offset 0)
    (Math/min (+ offset limit) (count xs))))

(e/defn Window [query! offset limit]
  (e/server
    (let [[n xs] ($ e/Offload #(let [xs (query!)]
                                 [(count xs)
                                  (window xs offset limit)]))]
      [n (e/diff-by identity xs)])))

(e/defn TableScrollFixedCounted
  "Scrolls like google sheets. this can efficiently jump through a large indexed collection"
  [Page-fn Record-fn]
  (e/client
    (dom/div (dom/props {:class "viewport" :style {:overflowX "hidden" :overflowY "auto"}})
      (let [!record-count (atom 20) ; initial guess
            record-count (e/watch !record-count)
            row-height 22 ; todo relative measurement (note: browser zoom impacts px height)
            [scrollTop] (e/input (scroll-state< dom/node))
            [_ clientHeight] ($ ResizeObserver dom/node)
            max-height (* record-count row-height)
            clamped-scroll-top (js/Math.min scrollTop max-height)
            offset (js/Math.floor (/ clamped-scroll-top row-height)) ; quantize scroll (no fractional row visibility)
            padding-top 0 ; e.g. sticky header row
            page-size (Math/floor (/ (- clientHeight padding-top) row-height))]
        (dom/table (dom/props {:style {:height (str (* row-height record-count) "px") ; optional absolute scrollbar
                                     :padding-top (str clamped-scroll-top "px") ; seen elements are replaced with padding
                                     :padding-bottom (str (- max-height clamped-scroll-top) "px")
                                     :display :grid}})

          (let [[record-count xs] ($ Page-fn offset page-size)]
            (reset! !record-count record-count)
            (e/cursor [id xs]
              (dom/tr (dom/props {:style {:display               :grid
                                          :grid-template-columns :subgrid
                                          :grid-column           "1 / -1"}})
                (e/cursor [Value ($ Record-fn id)]
                  (dom/td
                    ($ Value)))))))))))

(e/defn Teeshirt-orders [db search offset limit]
  (e/server ($ Window (partial teeshirt-orders db search) offset limit)))

(e/defn Genders [db search] ; todo window
  (e/server (e/diff-by identity ($ e/Offload #(genders db search)))))

(e/defn Shirt-sizes [db gender search] ; todo window
  (e/server (e/diff-by identity ($ e/Offload #(shirt-sizes db gender search)))))

(e/defn WebviewScroll []
  (e/client
    (let [db (e/server (e/watch conn))
          !search (atom "") search (e/watch !search)]

      (dom/element "style" ; Requires css {box-sizing: border-box;}
        (dom/text ".header { position: fixed; z-index:1; top: 0; left: 0; right: 0; height: 100px; background-color: #abcdef; }"
          ".footer { position: fixed; bottom: 0; left: 0; right: 0; height: 100px; background-color: #abcdef; }"
          ".viewport { position: fixed; top: 100px; bottom: 100px; left: 0; right: 0; background-color: #F63; overflow: auto; }"))
      (dom/div (dom/props {:class "header"})
        (dom/dl
          (dom/dt (dom/text "scroll debug state"))
          (dom/dd (dom/pre (dom/text (pr-str (update-keys (e/watch !scrollStateDebug) unqualify)))))
          (dom/dt (dom/text "search"))
          (dom/dd (reset! !search ($ SearchInput)))))

      ($ TableScrollFixedCounted
        ($ e/Partial Teeshirt-orders db search)

        (e/fn Record [id]
          (let [!e (e/server (d/entity db id))
                email (e/server (-> !e :order/email))
                gender (e/server (-> !e :order/gender :db/ident))
                shirt-size (e/server (-> !e :order/shirt-size :db/ident))]
            (e/amb
              (e/fn [] (dom/text id))
              (e/fn [] (dom/text email))
              (e/fn [] ($ Typeahead gender (e/fn [search] ($ Genders db search))))
              (e/fn [] ($ Typeahead shirt-size (e/fn [search] ($ Shirt-sizes db gender search)))))))))

    (dom/div (dom/props {:class "footer"})
      (dom/text "Try scrolling to the top, and resizing the window."))))
