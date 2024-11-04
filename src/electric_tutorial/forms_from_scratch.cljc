(ns electric-tutorial.forms-from-scratch
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.input-zoo0 :refer [Input Checkbox Input! Checkbox!]]
            [electric-tutorial.forms-from-scratch-form :refer [Query-record]]))

(e/defn FormsFromScratch []) ; boilerplate for tutorial framework

(e/defn DemoInputNaive []
  (let [s (dom/input ; dom elements return their final child value
            (dom/props {:class "foo" :maxlength 100})
            (dom/On "input" #(-> % .-target .-value) ""))]
    (dom/code (dom/text (pr-str s)))))

(e/defn DemoInputCircuit3 []
  (let [s (e/with-cycle [s ""]
            (Input s :class "foo" :maxlength 100))]
    (dom/code (dom/text (pr-str s)))))

(e/defn DemoInputCircuit []
  (let [!s (atom "") s (e/watch !s)
        s' (Input s :class "foo" :maxlength 100)]
    (reset! !s s')
    (dom/code (dom/text (pr-str s)))))

(e/defn DemoInputCircuit2 []
  (let [F (e/fn F [s] #_(recur (Input s)))] ; infinite loop :(
    (dom/code (dom/text (pr-str (F ""))))))

(e/defn DemoInputCircuit4 []
  (let [!s (atom "") s (e/watch !s)]
    (reset! !s (Input s))
    (reset! !s (Input s))
    (dom/code (dom/text (pr-str s)))))

(e/defn DemoInputCircuit5 []
  (let [!s (atom "") s (e/watch !s)
        s' (e/amb ; "table" of two values in superposition
             (Input s)
             (Input s))]
    (reset! !s s') ; auto-map `reset!` over table s'
    (dom/code (dom/text (pr-str s)))))

(e/defn DemoInputCircuit6 []
  (let [s (e/with-cycle [s ""]
            (e/amb ; table
              (Input s)
              (Input s)))]
    (dom/code (dom/text (pr-str s)))))

(def state0 {:user/str1 "hello" :user/num1 42 :user/bool1 true})

(e/defn DemoUserForm []
  (let [m (e/with-cycle [m state0]
            (let [{:keys [user/str1 user/num1 user/bool1]} m]
              (dom/fieldset
                {:user/str1 (Input str1)
                 :user/num1 (-> (Input num1 :type "number") parse-long)
                 :user/bool1 (Checkbox bool1)})))]
    (dom/code (dom/text (pr-str m)))))

; Async

(e/defn DemoToken []
  (let [slow (dom/div (Checkbox true :label "latency"))
        fail (dom/div (Checkbox true :label "failure"))
        !x (e/server (atom true)) x (e/server (e/watch !x))]
    slow fail
    (when-some [t (dom/button (dom/text "toggle!")
                    (let [e (dom/On "click" identity nil)
                          [t err] (e/RetryToken e)]
                      (dom/props {:aria-busy (some? t) :disabled (some? t)
                                  :aria-invalid (some? err)})
                      t))] ; encapsulate error
      (let [res (e/server
                  (e/Offload
                    (fn []
                      (when (true? slow) (Thread/sleep 1000))
                      (if fail ::rejected (do (swap! !x not) ::ok)))))]
        (case res
          ::ok (t) ; success sentinel
          (t res)))) ; feed error back into originating control
    (dom/code (dom/text (pr-str x)))))

; slow* (dom/div (Checkbox true :label "latency"))
; todo fix crash on cancellation when typing faster than latency
; (when (true? slow*) (Thread/sleep 1000))
; Note: the crash doesn't actually matter because we're about to wrap `Input!` into a `Form!` which is going to change the interaction pattern in an interesting way. (But IIUC the crash is an Electric bug, we will fix it)

(e/defn UserFormServer1 [{:keys [user/str1 user/num1 user/bool1]}]
  (e/amb ; concurrent individual edits!
    (Input! :user/str1 str1) ; fields are named
    (Input! :user/num1 num1 :type "number" :parse parse-long)
    (Checkbox! :user/bool1 bool1)))

(e/defn DemoInputServer []
  (let [fail (dom/div (Checkbox true :label "failure"))
        !x (e/server (atom state0)) x (e/server (e/watch !x))
        edits (e/amb
                (dom/div (UserFormServer1 x))
                (dom/div (UserFormServer1 x)))]
    fail
    (e/for [[t kv] edits] ; concurrent edit processing
      (let [res (e/server
                  (if fail ::rejected (do (swap! !x merge kv) ::ok)))]
        (case res
          ::ok (t)
          (t res))))
    (dom/pre (dom/text (pr-str x)))))
