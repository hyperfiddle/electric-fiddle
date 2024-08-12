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

#?(:cljs (defn scroll-state [scrollable]
           (->> (m/observe
                  (fn [!]
                    (! [0 0 0])
                    (let [sample (fn [] (! [(.. scrollable -scrollTop) ; optimization - detect changes (pointless)
                                            (.. scrollable -scrollHeight) ; snapshot height to detect layout shifts in flipped mode
                                            (.. scrollable -clientHeight)]))] ; measured viewport height (scrollbar length)
                      (.addEventListener scrollable "scroll" sample #js {"passive" true})
                      #(.removeEventListener scrollable "scroll" sample))))
             (throttle 16) ; RAF interval
             (m/relieve {}))))

#?(:cljs (defn resize-observer [node]
           (m/relieve {}
             (m/observe (fn [!] (! [(.-clientHeight node)
                                    (.-clientWidth node)])
                          (let [obs (new js/ResizeObserver
                                      (fn [entries]
                                        (let [content-box-size (-> entries (aget 0) .-contentBoxSize (aget 0))]
                                          (! [(.-blockSize content-box-size)
                                              (.-inlineSize content-box-size)]))))]
                            (.observe obs node) #(.unobserve obs)))))))

(e/defn SearchInput []
  (dom/input (dom/props {:placeholder "Filter..."})
    ($ dom/On "input" #(-> % .-target .-value) "")))

(e/defn Typeahead [v-id Options & [OptionLabel]]
  (e/client
    (dom/div (dom/props {:class "hyperfiddle-typeahead"})
      (let [OptionLabel (or OptionLabel (e/fn [x] (pr-str x)))
            container dom/node
            !v-id (atom v-id) v-id (e/watch !v-id)]
        (dom/input
          (dom/props {:placeholder "Filter..."})
          (if-some [close! ($ e/Token ($ dom/On "focus"))]
            (let [search ($ dom/On "input" #(-> % .-target .-value) "")]
              (binding [dom/node container] ; portal
                (dom/ul
                  (e/for [id ($ Options search)]
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
    (let [xs ($ e/Offload #(query!))] ; retain and reuse xs as offset changes
      [(count xs) (e/diff-by identity (window xs offset limit))])))

(e/defn Query-page-size [row-height padding-top node]
  (e/client
    (let [[clientHeight] (e/input (resize-observer node))
          page-size (Math/ceil (/ (- clientHeight padding-top) row-height))]
      page-size)))

(e/defn Query-offset [record-count row-height page-size overquery-factor node]
  (e/client
    (let [max-height (* record-count row-height)
          [scrollTop] (e/input (scroll-state node)) ; smooth scroll has already happened, cannot quantize the scroll
          clamped-scroll-top (js/Math.min scrollTop max-height) ; dangerous, not quantized to record boundary
          offset (js/Math.floor (/ clamped-scroll-top row-height)) ; quantized

          ; overquery strategy - load more below only for simpler math at boundaries
          q-limit (* page-size overquery-factor)
          occluded (- q-limit page-size)
          q-offset offset #_(- offset (js/Math.floor (/ occluded 2))) ; todo truncate at boundaries

          padding-top (* offset row-height) ; reconstruct padding from quantized offset
          occluded-height (* occluded row-height) ; todo truncate at boundary
          padding-bottom (- max-height padding-top occluded-height)]
      [q-offset q-limit padding-top padding-bottom])))

(e/defn TableScrollFixedCounted
  "Scrolls like google sheets. this can efficiently jump through a large indexed collection"
  [Page-fn Record-fn]
  (e/client
    (dom/div (dom/props {:class "viewport" :style {:overflowX "hidden" :overflowY "auto"}})
      (let [row-height 25 ; todo relative measurement (note: browser zoom impacts px height)
            padding-top 0 ; e.g. sticky header row
            !record-count (atom 25) ; initial guess
            record-count (e/watch !record-count)
            page-size ($ Query-page-size row-height padding-top dom/node)
            overquery-factor 1
            [q-offset q-limit padding-top padding-bottom] ($ Query-offset record-count row-height page-size overquery-factor dom/node)
            [record-count xs] ($ Page-fn q-offset q-limit)]
        (reset! !record-count record-count)
        (dom/table (dom/props {:style
                               (merge
                                 {:height (str (* row-height record-count) "px") ; optional absolute scrollbar
                                  :display "grid"
                                  :grid-template-columns "4em 15em min-content min-content"}
                                 (e/server ; align spacer latency with updated resultset
                                   {:padding-top (str padding-top "px") ; seen elements are replaced with padding
                                    :padding-bottom (str padding-bottom "px")}))})
          (e/for [id xs]
            (dom/tr (dom/props {:style {:display               "grid"
                                        :grid-template-columns "subgrid"
                                        :grid-column           "1 / f-1"
                                        :height (str row-height "px")}})
              (e/for [Value ($ Record-fn id)]
                (dom/td (dom/props {:style {:height (str row-height "px")}})
                  ($ Value))))))))))

(e/defn Teeshirt-orders [db search offset limit]
  (e/server ($ Window (partial teeshirt-orders db search) offset limit)))

(e/defn Genders [db search] ; todo limit
  (e/server (e/diff-by identity ($ e/Offload #(genders db search)))))

(e/defn Shirt-sizes [db gender search] ; todo limit
  (e/server (e/diff-by identity ($ e/Offload #(shirt-sizes db gender search)))))

(e/defn WebviewScroll []
  (e/client
    (let [db (e/server (e/watch conn))
          !search (atom "") search (e/watch !search)]

      (dom/element "style" ; Requires css {box-sizing: border-box;}
        (dom/text ".header { position: fixed; z-index:1; top: 0; left: 0; right: 0; height: 100px; background-color: #abcdef; }"
          ".viewport { position: fixed; top: 100px; bottom: 0px; left: 0; right: 0; background-color: #F63; overflow: auto; }"))
      (dom/div (dom/props {:class "header"})
        (dom/p (dom/text "Try scrolling, and resizing the window."))
        (dom/dl
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
              (e/fn [] ($ Typeahead gender (e/fn [search] ($ Genders db search))) #_(dom/text (name gender)))
              (e/fn [] ($ Typeahead shirt-size (e/fn [search] ($ Shirt-sizes db gender search))) #_(dom/text (name shirt-size))))))))))
