(ns london-talk-2024.webview-scroll
  (:require [clojure.math :as math]
            #?(:clj [models.teeshirt-orders-datascript-dustin :refer [conn teeshirt-orders]])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [london-talk-2024.webview-dynamic :refer [Row]]
            #?(:cljs [london-talk-2024.dom-scroll-helpers :refer [scroll-state resize-observer]])))

(e/defn Tap-diffs [x] (println 'diff (pr-str (e/input (e/pure x)))) x)

(defn window [cnt xs offset limit]
  (subvec (vec xs) ; fast cast
    (max offset 0)
    (min (+ offset limit) cnt)))

(e/defn Window [query! offset limit]
  (let [xs (e/Offload #(query!)) ; retain and reuse xs as offset changes
        n (count xs)
        ; (mapv vector (range offset limit) (window n xs offset limit)) -- broken, why?
        indexed-page (window n (map-indexed vector xs) offset limit)]
    [n (e/diff-by second indexed-page)]))

(e/defn Teeshirt-orders [db search sort-key offset limit]
  (e/server (Window (partial teeshirt-orders db search sort-key) offset limit)))

(defn clamp [n left right] (min (max n left) right))

(defn compute-overquery [overquery-factor record-count offset limit]
  (let [q-limit (* limit overquery-factor)
        occluded (clamp (- q-limit limit) 0 record-count)
        q-offset (clamp (- offset (math/floor (/ occluded overquery-factor))) 0 record-count)]
    [q-offset q-limit]))

(defn compute-scroll-window [row-height record-count clientHeight scrollTop]
  (let [padding-top 0 ; e.g. sticky header row
        limit (math/ceil (/ (- clientHeight padding-top) row-height)) ; aka page-size
        offset (int (/ (clamp scrollTop 0 (* record-count row-height)) ; prevent overscroll past the end
                      row-height))
        [offset limit] (compute-overquery 1 record-count offset limit)]
    [offset limit]))

(e/defn Scroll-window [row-height record-count node]
  (e/client ; don't roundtrip destructuring
    (let [[clientHeight] (e/input (resize-observer node))
          [scrollTop] (e/input (scroll-state node))] ; smooth scroll has already happened, cannot quantize
      (compute-scroll-window row-height record-count clientHeight scrollTop))))

(e/defn TableScrollFixedCounted [colspec Query Row] ; todo remove all organic layout like studio
  (dom/style (dom/text ".TableScrollFixedCounted tr { display:grid; grid-template-columns: 4em 15em min-content min-content; grid-column: 1 / f-1; }"))
  (dom/div (dom/props {:class "TableScrollFixedCounted"
                       :style {:overflowX "hidden" :overflowY "auto" ; Requires css {box-sizing: border-box;}
                               :position "fixed" :top "0" :bottom "0" :left "0" :right "0"}})
    (let [row-height 25 ; todo relative measurement (note: browser zoom impacts px height)
          !record-count (e/client (atom 0)) record-count (e/client (e/watch !record-count))
          o-l #_[offset limit] (Scroll-window row-height record-count dom/node)
          offset (e/client (nth o-l 0))
          limit (e/client (nth o-l 1))]
      (dom/table
        #_(dom/props ; uncomment for quantized scroll
            (e/client {:style {:position "fixed" :transform (str "translate(0," (- (* offset row-height)) "px)")}}))
        (let [[record-count indexed-page] (Query offset limit)] ; neutral
          (e/client (reset! !record-count record-count))
          (e/client (Tap-diffs indexed-page))
          (e/for [[i x] indexed-page]
            (dom/tr (dom/props {:style {:height (str row-height "px") ; todo shouldn't need this
                                        :position "absolute" :top (str (* i row-height) "px")}})
              (let [m (Row x)]
                (e/for [k colspec]
                  (dom/td
                    (e/call (get m k)))))))))
      (dom/div (dom/props {:style {:height (str (* row-height record-count) "px")}})))))

#?(:cljs (def !colspec (atom [:db/id :order/email :order/gender :order/shirt-size])))
#?(:cljs (def !sort-key (atom [:order/email])))

(e/defn WebviewScroll []
  (e/server
    (let [db (e/watch conn)
          colspec (e/client (e/diff-by identity (e/watch !colspec)))
          search (e/client (dom/input (dom/On "input" #(-> % .-target .-value) "")))]
      (TableScrollFixedCounted
        colspec
        (e/Partial Teeshirt-orders db "" (e/client (e/watch !sort-key)))
        (e/Partial Row db)))))

(comment
  (reset! !colspec [:db/id])
  (swap! !colspec conj :order/email)
  (swap! !colspec conj :order/gender)
  (swap! !colspec conj :order/shirt-size)

  (reset! !sort-key [:db/id])
  (reset! !sort-key [:order/shirt-size :db/ident])
  )