(ns optimistic-todomvc.todo-style
  (:require
   [hyperfiddle.electric-de :as e]
   hyperfiddle.electric-dom3
   [hyperfiddle.electric-css3 :as css]))

(def COLOR-DIRTY   "rgba(255,255,0,0.15)")
(def COLOR-PENDING "rgba(255,157,0,0.15)")
(def COLOR-ACCEPTED "rgba(0,255,0,0.10)")
(def COLOR-REJECTED "rgba(255,0,0,0.25)")

(e/defn Style []
  (e/client
    (css/style
      (css/rule "body.hyperfiddle"
        {:background-color "#F6F6F5"
         :display          :flex
         :flex-direction   :column
         :align-items      :center
         ;; :max-width "800px"
         }
        (css/rule "h1, input" {:font-family "HelveticaNeue, Helvetica"})
        (css/rule "h1" {:color       "#D7D7D6"
                        :text-shadow "-1px -1px rgba(0, 0, 0, 0.2)"
                        :font-size   "70px"
                        :text-align  :center})
        (css/rule ".todomvc-wrapper"
          {:display               :grid
           :gap "1rem"
           :grid-template-columns "auto auto"
           :max-width "1000px"
           :width "100%"})
        (css/rule ".todomvc"
          {:display        :flex
           :max-width      "800px"
           :width          "100%"
           :flex-direction :column
           :align-items    :stretch
           :border-radius  "2px"
           :background     "rgba(255, 255, 255, 0.9)"
           :box-shadow     "0 2px 6px 0 rgba(0, 0, 0, 0.2), 0 25px 50px 0 rgba(0, 0, 0, 0.15)"}
          (css/rule "&:before" {:content    "''"
                                :height     "15px"
                                :background "-webkit-linear-gradient(top, rgba(132, 110, 100, 0.8), rgba(101, 84, 76, 0.8))"})
          (css/rule "> input"
            {:border        :none, :outline :none
             :padding       "16px" #_       "16px 16px 16px 56px"
             :font-size     "24px"
             :line-height   "1.4em"
             :background    "rgba(0, 0, 0, 0.02)"
             :border-bottom "2px dotted lightgray"}
            (css/rule "&::placeholder"
              {:font-style :italic
               :color      "#a9a9a9"})
            (css/rule "&~.field-error" ; input's sibling (~) with .field-error class
              {:display :none}) ; HACK not needed in next design iteration
            )
          (css/rule "ul"
            {:margin 0, :padding 0, :list-style :none}
            (css/rule "li"
              {:position              :relative
               :display               :grid
               :grid-template-columns "auto 1fr"
               ;; :gap                   "2px"
               :font-size             "24px"
               :border                "2px dotted transparent"
               :border-bottom         "2px dotted lightgray"}
              (css/rule "input[type='checkbox']"
                {:grid-column  1
                 :grid-row     1
                 :appearance   :none
                 :text-align   :center
                 :width        "40px"
                 :border-right "2px solid #f5d6d6"
                 :margin       0}
                (css/rule "&::after"
                  {:content         "'✔'"
                   :line-height     "43px"
                   :font-size       "20px"
                   :color           "#d9d9d9"
                   :text-shadow     "0 -1px 0 #bfbfbf"
                   :height          "100%"
                   :display         :flex
                   :align-items     :center
                   :justify-content :center})
                (css/rule "&:focus::after"
                  {:border "2px solid"})
                (css/rule "&:checked::after"
                  {:color       "#85ada7"
                   :text-shadow "0 1px 0 #669991"
                   :bottom      "1px"
                   :position    :relative})
                (css/rule "&:checked + input:not(:focus)"
                  {:text-decoration :line-through
                   :color           "#a9a9a9"}))
              (css/rule "input[type='text']"
                {:grid-column      2
                 :grid-row         1
                 :position         :relative
                 :padding          "15px"
                 :font-size        :inherit
                 :line-height      "1.2"
                 :transition       "color 0.4s"
                 :color            "rgba(0, 0, 0, 0.6)"
                 :background-color :transparent
                 :border           :none
                 :border-left      "2px solid #f5d6d6"}
                (css/rule "&:focus"
                  {:box-shadow "0 0 0.25rem lightgray inset"
                   :outline    "1px gray solid"
                   :z-index    "1"})
                (css/rule "&.pending" {:background-color COLOR-PENDING})
                (css/rule "&.accepted" {:background-color COLOR-ACCEPTED})
                (css/rule "&.rejected" {:background-color COLOR-REJECTED})))
            (css/rule "li input.accepted"
              {:background-color COLOR-ACCEPTED})
            (css/rule "li input.rejected"
              {:background-color COLOR-REJECTED})
            (css/rule "li .field-error"
              {:grid-column      "1/4"
               :font-size        "1rem"
               :color            :orangered
               :text-align       :justify
               :padding          ".25rem .75rem"
               :background-color COLOR-REJECTED})
            (css/rule "li input.dirty"
              {:background-color COLOR-DIRTY})
            (css/rule "li input.pending"
              {:background-color COLOR-PENDING})
            (css/rule "li:has(.pending)::before, li.pending::before"
              {:content       "''"
               :position      :absolute
               :box-sizing    :border-box
               :z-index       2
               :top           "1.2rem" ; Same as input's line-height
               :right         "1rem"
               :width         "20px"
               :height        "20px"
               :border-top    :none
               :border        "2px gray solid"
               :border-radius "50%"
               :font-size     "24px"
               :animation     "spin 1s linear infinite"})
            (css/rule "li .retry-container"
              {:background-color COLOR-REJECTED
               :grid-row         1, :grid-column 3
               :display          :flex
               :align-items      :center}
              (css/rule "button"
                {:border           "1px lightgray solid"
                 :border-radius    "3px"
                 :font-size        "1.25rem"
                 :padding          "0.25rem 0.5rem"
                 :font-variant     :small-caps
                 :background-color :white
                 :cursor           :pointer
                 :margin           "auto 1rem"}
                (css/rule "&.pending"
                  {:display :none}))))))

      (css/keyframes "spin"
        (css/keyframe :from {:transform "rotate(0deg)"})
        (css/keyframe :to   {:transform "rotate(360deg)"}))

      (css/rule ".legend" {:width "30rem"}))))
