(ns electric-tutorial.forms3a-form
  (:require #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.cqrs0 :as cqrs :refer [Form Service]]
            [hyperfiddle.input-zoo0 :refer [Input! Checkbox!]]))

#?(:clj (def !conn))

(e/defn UserForm [db id]
  (dom/fieldset (dom/legend (dom/text "transactional form"))
    (let [{:keys [user/str1 user/num1 user/bool1]}
          (e/server (e/Offload #(d/pull db [:user/str1 :user/num1 :user/bool1] id)))]
      (Form ; buffer and batch edits into an atomic form
        (dom/dl
          (e/amb ; concurrent field edits (which get us field dirty state).
            ; if we used e.g. a vector/map aggregator like before, we'd need
            ; circuit controls and therefore lose field dirty state.
            (dom/dt (dom/text "str1")) (dom/dd (Input! str1 :parse #(hash-map :a %))) ; gross identity per edit
            (dom/dt (dom/text "num1")) (dom/dd (Input! num1 :type "number" :parse #(hash-map :b (parse-long %))))
            (dom/dt (dom/text "bool1")) (dom/dd (Checkbox! bool1 :parse #(hash-map :c %)))))
        :commit (fn [dirty-vs guess]
                  (let [{:keys [a b c] :or {a str1 b num1 c bool1}} (apply merge dirty-vs)]
                    [[`UserFormSubmit id a b c] guess]))
        :debug true))))

(e/defn UserFormSubmit [id str1 num1 bool1]
  #_(e/server (prn 'UserFormSubmit id str1 num1 bool1))
  (e/server ; secure command interpretation, validate command here
    (let [tx [{:db/id id :user/str1 str1 :user/num1 num1 :user/bool1 bool1}]]
      (e/Offload #(try (d/transact! !conn tx) (doto ::cqrs/ok (prn 'UserFormSubmit))
                    (catch Exception e (doto ::fail (prn 'UserFormSubmit e))))))))

(e/defn Forms3a-form []
  (let [db (e/server (e/watch !conn))]
    (Service {`UserFormSubmit UserFormSubmit}
      (UserForm db 42))))