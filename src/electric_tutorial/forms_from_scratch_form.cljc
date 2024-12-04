(ns electric-tutorial.forms-from-scratch-form
  (:require #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms0 :refer [Form! Checkbox Input! Checkbox!]]))

#?(:clj (defonce !conn (doto (d/create-conn {})
                         (d/transact! [{:db/id 42 :user/str1 "one"
                                        :user/num1 1 :user/bool1 true}]))))

#?(:clj (defn transact-unreliable [!conn tx
                                   & {:keys [slow fail]
                                      :or {slow false fail false}}]
          (when (true? slow) (Thread/sleep 1000))
          (when (true? fail) (throw (ex-info "artificial failure" {})))
          (d/transact! !conn tx)))

(e/declare debug*)
(e/declare slow*)
(e/declare fail*)

(e/defn Query-record [db id]
  (e/server (e/Offload #(d/pull db [:user/str1 :user/num1 :user/bool1] id))))

(e/defn UserFormServer [db id]
  (dom/fieldset (dom/legend (dom/text "UserFormServer"))
    (let [{:keys [user/str1 user/num1 user/bool1] :as m} (Query-record db id)]
      (Form! ; buffer and batch edits into an atomic form
        (dom/dl
          (e/amb
            (dom/dt (dom/text "str1")) (dom/dd (Input! :user/str1 str1))
            (dom/dt (dom/text "num1")) (dom/dd (Input! :user/num1 num1 :type "number" :parse parse-long))
            (dom/dt (dom/text "bool1")) (dom/dd (Checkbox! :user/bool1 bool1))))
        :commit (fn [dirty-form]
                  (let [{:keys [user/str1 user/num1 user/bool1] :as m}
                        (merge m dirty-form)]
                    [[`User-form-submit id str1 num1 bool1] ; command
                     {id m}])) ; prediction
        :debug debug*))))

(e/defn User-form-submit [id str1 num1 bool1]
  (e/server ; secure, validate command here
    (let [tx [{:db/id id :user/str1 str1 :user/num1 num1 :user/bool1 bool1}]]
      (e/Offload #(try (transact-unreliable !conn tx :fail fail* :slow slow*)
                    ::ok ; sentinel
                    (catch Exception e (doto ::fail (prn e))))))))

(e/defn UserService [edits]
  (e/client ; client bias, t doesn't transfer
    (e/for [[t [effect & args :as form] guess] edits] ; concurrent edits
      (let [res (case effect
                  `User-form-submit (e/apply User-form-submit args)
                  (prn 'unmatched effect))]
        (case res
          ::ok (t) ; sentinel, any other value is an error
          (t res)))))) ; feed error back into control to prompt for retry

(e/defn DemoFormServer1 []
  (binding [debug* (Checkbox true :label "debug")
            slow* (Checkbox true :label "latency")
            fail* (Checkbox false :label "failure")]
    debug* fail* slow*

    (let [db (e/server (e/watch !conn))
          edits (UserFormServer db 42)]
      (UserService edits))))
