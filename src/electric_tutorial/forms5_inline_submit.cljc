(ns electric-tutorial.forms5-inline-submit
  (:require #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.cqrs0 :as cqrs :refer [Field Stage]]
            [hyperfiddle.input-zoo0 :refer [Input! Checkbox!]]
            [electric-tutorial.forms3-crud :refer
             [Service #?(:clj !conn)]]))

(e/defn UserForm [db id]
  (dom/fieldset
    (let [{:keys [user/str1 user/num1 user/bool1]}
          (e/server (d/pull db [:user/str1 :user/num1 :user/bool1] id))]
      (dom/dl
        (e/amb
          (dom/dt (dom/text "str1"))
          (dom/dd (Stage ; field stage
                    (Field id :user/str1
                      (Input! str1))))

          (dom/dt (dom/text "num1"))
          (dom/dd (Stage ; field stage
                    (Field id :user/num1
                      (e/for [[t v] (Input! num1 :type "number")]
                        [t (parse-long v)]))))

          (dom/dt (dom/text "bool1"))
          (dom/dd (Stage ; field stage
                    (Field id :user/bool1
                      (Checkbox! bool1)))))))))

(e/defn Forms5-inline-submit []
  (let [db (e/server (e/watch !conn))
        edits (e/amb
                (UserForm db 42) #_(Stage :debug true)
                (UserForm db 42) #_(Stage :debug true))]
    (Service edits)))