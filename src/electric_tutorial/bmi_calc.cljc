(ns electric-tutorial.bmi-calc
  (:require
    [hyperfiddle.electric3 :as e]
    [hyperfiddle.electric-dom3 :as dom]))

(e/defn RangeInput [v min_ max_]
  (dom/input (dom/props {:style {:width "100%"} :type "range" :value v :min min_ :max max_})
    (dom/On "input" #(-> % .-target .-value) v)))

(defn weight [h bmi] (* bmi (/ (* h h) 10000)))

(defn bmi-category [bmi low norm]
  (cond
    (< bmi low) ::underweight
    (< bmi norm) ::normal
    :else ::overweight))

(declare css)
(e/defn BMICalculator []
  (dom/style (dom/text css))
  (let [!h (atom 170) h (e/watch !h)
        !w (atom 70) w (e/watch !w)]
    (dom/div (dom/text "Height: " h "cm"))
    (some->> (RangeInput h 100 220) js/parseFloat (reset! !h))
    (dom/div (dom/text "Weight: " (.toFixed w) "kg"))
    (some->> (RangeInput w 30 150) js/parseFloat (reset! !w))
    (let [bmi (* (/ w (* h h)) 10000)
          category (bmi-category bmi 18.50 25.01)]
      (dom/div (dom/text "BMI: " (.toFixed bmi 2) " ")
        (dom/span (dom/props {:class (name category)}) (dom/text (name category))))
      (some->> (RangeInput bmi 9 52) js/parseFloat (weight h) (reset! !w)))))

(def css ".underweight { color: red; } .normal { color: green; } .overweight { color: orange; }")
