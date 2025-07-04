(ns electric-tutorial.dir-tree
  (:require #?(:clj clojure.java.io)
            [dustingetz.str :refer [includes-str?]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]))

(e/defn Dir-tree* [handle search]
  (e/server
    (let [name_ (.getName handle)]
      (cond
        (.isDirectory handle)
        (dom/li (dom/text name_)
          (dom/ul
            (let [children (.listFiles handle)]
              (e/for [handle (e/diff-by identity children)]
                (Dir-tree* handle search)))))

        (and (.isFile handle) (includes-str? name_ search))
        (dom/li (dom/text name_))))))

(e/defn DirTree []
  (e/client
    (let [search (dom/input (dom/On "input" #(-> % .-target .-value) ""))
          handle (e/server (clojure.java.io/file "src/docs_site"))]
      (dom/ul
        (Dir-tree* handle search)))))