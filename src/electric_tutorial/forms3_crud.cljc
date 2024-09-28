(ns electric-tutorial.forms3-crud
  (:require #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            #_[hyperfiddle.electric-dom3 :as dom]
            [electric-tutorial.forms3a-form :refer [Forms3a-form]]
            [electric-tutorial.forms3b-inline-submit :refer [Forms3b-inline-submit]]
            [electric-tutorial.forms3c-inline-submit-builtin :refer [Forms3c-inline-submit-builtin]]
            [electric-tutorial.forms3d-autosubmit :refer [Forms3d-autosubmit]]))

#?(:clj (defonce !conn (doto (d/create-conn {})
                         (d/transact! [{:db/id 42 :user/str1 "one"
                                        :user/num1 1 :user/bool1 true}]))))

(e/defn Forms3-crud []
  (Forms3a-form)
  (Forms3b-inline-submit)
  (Forms3c-inline-submit-builtin)
  (Forms3d-autosubmit))

; deal with circular refs, todo markdown extension dependency injection
#?(:clj (alter-var-root #'electric-tutorial.forms3a-form/!conn (constantly !conn)))