(ns london-talk-2024.webview-scroll
  (:require #?(:clj [models.teeshirt-orders-datascript-dustin :refer [conn teeshirt-orders]])
            [hyperfiddle.electric-de :as e :refer [$]]
            [hyperfiddle.electric-dom3 :as dom]
            [london-talk-2024.webview-dynamic :refer [Row]]
            #?(:cljs [london-talk-2024.dom-scroll-helpers :refer [scroll-state resize-observer]])))

(e/defn Tap-diffs [x] (println 'diff (pr-str (e/input (e/pure x)))) x)

(defn window [xs offset limit]
  (subvec (vec xs) ; fast cast
    (Math/max offset 0)
    (Math/min (+ offset limit) (count xs))))

(e/defn Window [query! offset limit]
  (e/server
    (let [xs ($ e/Offload #(query!))] ; retain and reuse xs as offset changes
      [(count xs) (e/diff-by identity (window xs offset limit))])))

(e/defn Teeshirt-orders [db search sort-key offset limit]
  (e/server ($ Window (partial teeshirt-orders db search sort-key) offset limit)))

(e/defn TableScrollFixedCounted [colspec Query Row]
  (e/client
    (dom/div (dom/props {:style {:overflowX "hidden" :overflowY "auto" ; Requires css {box-sizing: border-box;}
                                 :position "fixed" :top "0" :bottom "0" :left "0" :right "0"}})
      (let [row-height 25 ; todo relative measurement (note: browser zoom impacts px height)
            padding-top 0 ; e.g. sticky header row
            !record-count (atom 25) ; initial guess
            record-count (e/watch !record-count)
            [clientHeight] (e/input (resize-observer dom/node))
            page-size (Math/ceil (/ (- clientHeight padding-top) row-height))
            overquery-factor 1
            max-height (* record-count row-height)
            [scrollTop] (e/input (scroll-state dom/node)) ; smooth scroll has already happened, cannot quantize the scroll
            clamped-scroll-top (js/Math.min scrollTop max-height) ; dangerous, not quantized to record boundary
            offset (js/Math.floor (/ clamped-scroll-top row-height)) ; quantized

            ; overquery strategy - load more below only for simpler math at boundaries
            q-limit (* page-size overquery-factor)
            occluded (- q-limit page-size)
            q-offset offset #_(- offset (js/Math.floor (/ occluded 2))) ; todo truncate at boundaries

            padding-top (* offset row-height) ; reconstruct padding from quantized offset
            occluded-height (* occluded row-height) ; todo truncate at boundary
            padding-bottom (- max-height padding-top occluded-height)]

        (dom/table
          (dom/props {:style {:display "grid" :grid-template-columns "4em 15em min-content min-content"}})
          (dom/props {:style {:height (str (* row-height record-count) "px")}}) ; record-count blinks on scroll, isolate
          (dom/props {:style (e/server ; align spacer latency with updated resultset
                               {:padding-top (str padding-top "px") ; seen elements are replaced with padding
                                :padding-bottom (str padding-bottom "px")})})
          (e/server
            (let [[record-count ids] ($ Query q-offset q-limit)]
              (e/client (reset! !record-count record-count))
              (e/client ($ Tap-diffs ids))
              (e/for [id ids]
                (dom/tr (dom/props {:style {:display "grid" :grid-template-columns "subgrid" :grid-column "1 / f-1" :height (str row-height "px")}})
                  (let [m ($ Row id)]
                    (e/for [k colspec]
                      (dom/td
                        ($ (get m k))))))))))))))

#?(:cljs (def !colspec (atom [:db/id :order/email :order/gender :order/shirt-size])))
#?(:cljs (def !sort-key (atom [:order/email])))

(e/defn WebviewScroll []
  (e/server
    (let [db (e/watch conn)
          colspec (e/client (e/diff-by identity (e/watch !colspec)))
          search (e/client (dom/input ($ dom/On "input" #(-> % .-target .-value) "")))]
      ($ TableScrollFixedCounted
        colspec
        ($ e/Partial Teeshirt-orders db "" (e/client (e/watch !sort-key)))
        ($ e/Partial Row db)))))

(comment
  (reset! !colspec [:db/id])
  (swap! !colspec conj :order/email)
  (swap! !colspec conj :order/gender)
  (swap! !colspec conj :order/shirt-size)

  (reset! !sort-key [:db/id])
  (reset! !sort-key [:order/shirt-size :db/ident])
  )