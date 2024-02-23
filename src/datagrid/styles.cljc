(ns datagrid.styles
  (:require
   [heroicons.electric.v24.outline :as icons]
   [hyperfiddle.electric :as e]
   [hyperfiddle.electric-css :as css]
   [hyperfiddle.electric-dom2 :as dom]))

(e/def IconStyle
  "Return a unique, generated class name to apply to multiple icons."
  (e/client
    (e/singleton
      (css/scoped-style
        (css/rule {:width "1rem", :height "1rem"})))))

(e/defn* PlusUpIcon []
  (e/client
    (dom/div
      (dom/props {:class (IconStyle.)
                  :style {:position :relative}})
      (icons/chevron-up (dom/props {:class (IconStyle.)
                                    :style {:position :absolute
                                            :top   "-0.45rem"}}))
      (icons/plus (dom/props {:class (IconStyle.)
                              :style {:position  :absolute
                                      :transform "scale(0.8)"}})))))

(e/defn* PlusDownIcon []
  (e/client
    (dom/div
      (dom/props {:class (IconStyle.)
                  :style {:position :relative}})
      (icons/chevron-down (dom/props {:class (IconStyle.)
                                      :style {:position :absolute
                                              :bottom   "-0.45rem"}}))
      (icons/plus (dom/props {:class (IconStyle.)
                              :style {:position  :absolute
                                      :transform "scale(0.8)"}})))))

(def SHADOW "0 1px 3px 0 rgb(0 0 0 / 0.1), 0 1px 2px -1px rgb(0 0 0 / 0.1)")
(def SHADOW-LG "0 10px 15px -3px rgb(0 0 0 / 0.1), 0 4px 6px -4px rgb(0 0 0 / 0.1)")

(e/defn* CellsStyle []
  (e/client
    (css/scoped-style
      (css/rule ".cell-input"
        {:padding     "1px 0"
         :width       "100%"
         :height      "100%"
         :border      :none
         :white-space :pre
         :font-family :monospace})
      (css/rule ".checkbox-cell"
        {:border          "1px #E1E1E1 solid"
         :font-size       "0.75rem"
         :line-height     "1rem"
         :display         :flex
         :align-items     :center
         :justify-content :center})
      (css/rule ".entry-cell"
        {:display       :block
         :overflow      :hidden
         :text-overflow :ellipsis
         :white-space   :nowrap
         :border        "1px #E1E1E1 solid"})
      (css/rule ".entry-cell:focus-within"
        {:border-color "rgb(37 99 235)" #_ "border-blue-600"}))))

(e/defn* GridStyle []
  (e/client
    (css/scoped-style
      (css/rule {:grid-auto-columns "auto", :border-collapse :collapse})
      (css/rule "td:focus-within" {:outline "1px lightgray solid"})
      (css/rule "tr td" {:background-color :white})
      (css/rule "tr.header td > *" {:background-color :lightgray})
      (css/rule "thead tr" {:box-shadow SHADOW :z-index 10})
      (css/rule "thead tr th" {:font-size        "0.75rem"
                               :line-height      "1rem"
                               :display          :flex
                               :align-items      :center
                               :justify-content  :center
                               :background-color "#EFEFEF"
                               :height           "30px"}))))
