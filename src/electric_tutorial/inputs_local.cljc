(ns electric-tutorial.inputs-local
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms5 :refer [Input Input* Checkbox]]))

(e/defn InputCicruit []) ; boilerplate

(e/defn DemoInputNaive []
  (let [s (dom/input ; dom elements return their final child value
            (dom/props {:class "foo" :maxlength 100})
            (dom/On "input" #(-> % .-target .-value) ""))]
    (dom/code (dom/text (pr-str s)))))

(e/defn DemoInputCircuit-uncontrolled []
  (let [s (Input* "" :class "foo" :maxlength 100)]
    (dom/code (dom/text (pr-str s))))
  (dom/br)
  (let [s (Input* "" :class "foo" :maxlength 100)]
    (dom/code (dom/text (pr-str s)))))

(e/defn DemoInputCircuit-controlled []
  (let [!s (atom "") s (e/watch !s)]
    (reset! !s (Input s))
    (reset! !s (Input s))
    (dom/code (dom/text (pr-str s)))))

(e/defn DemoInputCircuit-amb []
  (let [!s (atom "") s (e/watch !s)
        s' (e/amb ; "table" of two values in superposition
             (Input s)
             (Input s))]
    (reset! !s s') ; auto-map `reset!` over table s'
    (dom/code (dom/text (pr-str s)))))

(e/defn DemoInputCircuit-cycle []
  (let [s (e/with-cycle [s ""]
            (e/amb ; table
              (Input s)
              (Input s)))]
    (dom/code (dom/text (pr-str s)))))

(e/defn DemoInputCircuit4 [])

(e/defn DemoInputCircuit5 [])

(e/defn DemoInputCircuit6 []
  (let [s (e/with-cycle [s ""]
            (Input s :class "foo" :maxlength 100))]
    (dom/code (dom/text (pr-str s)))))

(def state0 {:user/str1 "hello" :user/num1 42 :user/bool1 true})

(e/defn DemoFormSync []
  (let [!m (atom state0) m (e/watch !m)
        {:keys [user/str1 user/num1 user/bool1]} m]
    (reset! !m
      {:user/str1 (Input str1)
       :user/num1 (-> (Input num1 :type "number") parse-long)
       :user/bool1 (Checkbox bool1)})
    (dom/pre (dom/text (pr-str m)))))

(e/defn DemoFormSync-cycle []
  (let [m (e/with-cycle [m state0]
            (let [{:keys [user/str1 user/num1 user/bool1]} m]
              {:user/str1 (Input str1)
               :user/num1 (-> (Input num1 :type "number") parse-long)
               :user/bool1 (Checkbox bool1)}))]
    (dom/pre (dom/text (pr-str m)))))

(e/defn Fiddles []
  {`InputCicruit InputCicruit
   `DemoInputNaive DemoInputNaive
   `DemoInputCircuit-uncontrolled DemoInputCircuit-uncontrolled
   `DemoInputCircuit-controlled DemoInputCircuit-controlled
   `DemoInputCircuit-amb DemoInputCircuit-amb
   `DemoInputCircuit-cycle DemoInputCircuit-cycle
   `DemoInputCircuit4 DemoInputCircuit4
   `DemoInputCircuit5 DemoInputCircuit5
   `DemoInputCircuit6 DemoInputCircuit6
   `DemoFormSync DemoFormSync
   `DemoFormSync-cycle DemoFormSync-cycle})