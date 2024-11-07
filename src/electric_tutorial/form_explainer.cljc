(ns electric-tutorial.form-explainer
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.cqrs0 :refer [Form!]]
            [hyperfiddle.input-zoo0 :refer [Input Checkbox Input! Checkbox!]]))

; slow* (dom/div (Checkbox true :label "latency"))
; todo fix crash on cancellation when typing faster than latency
; (when (true? slow*) (Thread/sleep 1000))
; Note: the crash doesn't actually matter because we're about to wrap `Input!` into a `Form!` which is going to change the interaction pattern in an interesting way. (But IIUC the crash is an Electric bug, we will fix it)

(def state0 {:user/str1 "hello" :user/num1 42 :user/bool1 true})

(e/defn UserFormServer1 [{:keys [user/str1 user/num1 user/bool1]}]
  (Form!
    (e/amb ; concurrent individual edits!
      (Input! :user/str1 str1) ; fields are named
      (Input! :user/num1 num1 :type "number" :parse parse-long)
      (Checkbox! :user/bool1 bool1))
    :commit (fn [dirty-form] [dirty-form nil])))

(e/defn FormExplainer []
  (let [fail (dom/div (Checkbox true :label "failure"))
        !x (e/server (atom state0)) x (e/server (e/watch !x))
        edits (e/amb
                (UserFormServer1 x)
                (UserFormServer1 x))]
    fail
    (e/for [[t dirty-form] edits] ; concurrent edit processing
      (let [res (e/server
                  (if fail ::rejected (do (swap! !x merge dirty-form) ::ok)))]
        (case res
          ::ok (t)
          (t res))))
    (dom/pre (dom/text (pr-str x)))))

(e/defn Grid [])