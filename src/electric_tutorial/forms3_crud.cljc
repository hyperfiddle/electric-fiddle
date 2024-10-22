(ns electric-tutorial.forms3-crud
  (:require #?(:clj [datascript.core :as d])))

#?(:clj (defonce !conn (doto (d/create-conn {})
                         (d/transact! [{:db/id 42 :user/str1 "one"
                                        :user/num1 1 :user/bool1 true}]))))

; deal with circular refs, todo markdown extension dependency injection
#?(:clj (alter-var-root #'electric-tutorial.forms3a-form/!conn (constantly !conn)))