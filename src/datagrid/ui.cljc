(ns datagrid.ui
  (:require
   #?(:cljs [goog.date.relative :refer [formatPast]])
   [clojure.string :as str]
   [contrib.color]
   [heroicons.electric.v24.outline :as icons]
   [hyperfiddle.electric :as e]
   [hyperfiddle.electric-css :as css]
   [hyperfiddle.electric-dom2 :as dom]
   [hyperfiddle.router :as router]))

(e/defn ClosePanelButton [path]
  (e/client
    (dom/button
      (dom/props {:title "Close panel"
                  :class (css/scoped-style
                           (css/rule {:width                      "1.25rem"
                                      :height                     "1.25rem"
                                      :position                   :absolute
                                      :top                        0
                                      :left                       0
                                      :right                      0
                                      :margin-left                :auto
                                      :margin-right               :auto
                                      :background-color           :white
                                      :box-sizing                 :content-box
                                      :padding                    "0.125rem 0.5rem"
                                      :box-shadow                 "0 0.25rem .75rem lightgray"
                                      :border                     "1px white solid"
                                      :border-bottom-right-radius "50%"
                                      :border-bottom-left-radius  "50%"
                                      :z-index                    2})
                           (css/rule ":hover" {:cursor :pointer
                                               :transform "scale(1.05)"}))})
      (icons/chevron-down)
      (dom/on "click" (e/fn* [] (router/Navigate!. path))))))

#?(:cljs
   (defn format-time [inst]
     (let [fmt (js/Intl.DateTimeFormat. js/undefined #js {:timeStyle "short"})]
       (.format fmt inst))))

#?(:cljs
   (defn format-date [inst]
     (let [fmt (js/Intl.DateTimeFormat. js/undefined #js {:dateStyle "short"})]
       (.format fmt inst))))

#?(:cljs
   (defn format-relative [inst]
     (let [now (js/Date.)]
       (not-empty
         (let [today?     (< (- now inst) (* 1000 60 60 24))
               yesterday? (< (- now inst) (* 1000 60 60 24 2))]
           (cond
             today?     (formatPast (.getTime inst))
             yesterday? (str (formatPast (.getTime inst)) " " (format-time inst))))))))

#?(:cljs
   (defn format-commit-time
     ([inst] (format-commit-time true inst))
     ([relative? inst]
      (or (and relative? (format-relative inst))
        (str (format-date inst) " " (format-time inst))))))

(defn format-branch [str] (str/replace-first str #"refs/(heads|remotes)/" ""))

(defn branch-color [branch-ref-name] (contrib.color/color branch-ref-name (/ 63 360) 55 65))

(e/defn LayoutStyle [show-detail?]
  (e/client
    (css/scoped-style
      (css/rule {:height         "100%"
                 :display        :grid
                 :gap            "0.75rem"
                 :grid-auto-flow :column
                 :overflow       :hidden
                 :grid-template  (css/grid-template [[[:search :search]   :min-content]
                                                     [[:refs    :log]     "1fr"]
                                                     (when show-detail?
                                                       [[:details :details] "40%"])]
                                   [:auto "1fr"])})
      (css/rule ".virtual-scroll" {:flex 1, :max-height "100%"})
      (css/rule ".datagrid > tr > td, .datagrid > thead th" {:padding-left "0.5rem", :padding-right "0.5em", :border :none})
      (css/rule ".datagrid > tr:nth-child(odd) > td" {:background-color :whitesmoke})
      (css/rule ".d2h-file-list-wrapper" {:position :sticky, :top 0, :height :min-content}))))
