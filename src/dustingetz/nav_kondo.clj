(ns dustingetz.nav-kondo
  (:require
   [clj-kondo.core :as kondo]
   [contrib.assert :refer [check]]))

(def !kondo (kondo/run! {:lint ["src"]
                         :config {:analysis true}}))
(defn kondo [] !kondo)

(comment
  (def x (kondo/run! {:lint ["src"]}))
  (kondo/print! x)

  (def x (with-in-str "(ns foo (:import [clojure.lang RT]))"
           (kondo/run! {:lint ["-"] :config {:analysis {:java-class-usages true}}})))

  (-> x :analysis :java-class-usages first :class) := "clojure.lang.RT"

  (def !kondo (clojure.edn/read-string (slurp "file-information.edn")))
  (def !kondo (time (kondo)))

  (defn kondo-ana-def
    [{:keys [row end-row filename]}]
    (with-open [reader (io/reader filename)]
      (-> reader
        (line-seq)
        (vec)
        (subvec (dec row) end-row)
        (->> (str/join "\n")))))

  (defn kondo-project-vars []
    (->> !kondo
      :analysis
      :var-definitions
      (group-by (fn [{:keys [ns name]}] (str ns \/ name)))
      (map (fn [[k [v]]] [k v]))
      (into {})))

  (defn kondo-project-vars-search [str]
    (let [names (keys (kondo-project-vars))]
      (if (str/blank? str)
        names
        (filter #(str/includes? % str) names))))

  (kondo-ana-def
    (get (kondo-project-vars) "app.todo-list/project-meta"))

  )