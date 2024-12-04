(ns electric-tutorial.forms3b-inline-submit
  (:require #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms0 :as forms :refer
             [Input! Checkbox! Checkbox Form! Service try-ok effects*]]
            [electric-tutorial.forms3a-form :refer
             [Query-record #?(:clj !conn) #?(:clj transact-unreliable)]]))

(e/declare debug*)
(e/declare slow*)
(e/declare fail*)
(e/declare show-buttons*)
(e/declare auto-submit*)

(e/defn UserForm [db id edits]
  (dom/fieldset (dom/legend (dom/text "UserForm"))
    (let [{:keys [user/str1 user/num1 user/bool1]} (Query-record db id edits)]
      (dom/dl
        (e/amb
          (dom/dt (dom/text "str1"))
          (dom/dd (Form! (Input! :user/str1 str1)
                    :commit (fn [{v :user/str1}]
                              [[`Str1FormSubmit id v] ; command
                               {id {:user/str1 v}}]) ; prediction
                    :auto-submit auto-submit*
                    :show-buttons show-buttons*
                    :debug debug*))

          (dom/dt (dom/text "num1"))
          (dom/dd (Form! (Input! :user/num1 num1 :type "number" :parse parse-long)
                    :commit (fn [{v :user/num1}]
                              [[`Num1FormSubmit id v]
                               {id {:user/num1 v}}])
                    :auto-submit auto-submit*
                    :show-buttons show-buttons*
                    :debug debug*))

          (dom/dt (dom/text "bool1"))
          (dom/dd (Form! (Checkbox! :user/bool1 bool1)
                    :commit (fn [{v :user/bool1}]
                              [[`Bool1FormSubmit id v]
                               {id {:user/bool1 v}}])
                    :auto-submit auto-submit*
                    :show-buttons show-buttons*
                    :debug debug*)))))))

(e/defn Str1FormSubmit [id v]
  (e/server
    (let [tx [{:db/id id :user/str1 v}]]
      (e/Offload #(try-ok (transact-unreliable !conn tx :fail fail* :slow slow*))))))

(e/defn Num1FormSubmit [id v]
  (e/server
    (let [tx [{:db/id id :user/num1 v}]]
      (e/Offload #(try-ok (transact-unreliable !conn tx :fail fail* :slow slow*))))))

(e/defn Bool1FormSubmit [id v]
  (e/server
    (let [tx [{:db/id id :user/bool1 v}]]
      (e/Offload #(try-ok (transact-unreliable !conn tx :fail fail* :slow slow*))))))

(e/defn Forms3b-inline-submit []
  (binding [effects* {`Str1FormSubmit Str1FormSubmit
                      `Num1FormSubmit Num1FormSubmit
                      `Bool1FormSubmit Bool1FormSubmit}
            debug* (Checkbox debug* :label "debug")
            slow* (Checkbox slow* :label "latency")
            fail* (Checkbox fail* :label "failure")
            show-buttons* (or (Checkbox show-buttons* :label "show-buttons") ::forms/smart)
            auto-submit* (Checkbox auto-submit* :label "auto-submit")]
    debug* fail* slow* auto-submit* show-buttons*
    (let [db (e/server (e/watch !conn))]
      (Service
        (e/with-cycle* first [edits (e/amb)]
          (UserForm db 42 edits))))))
