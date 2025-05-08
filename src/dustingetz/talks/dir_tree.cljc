(ns dustingetz.talks.dir-tree "http://localhost:8080/dustingetz.talks.dir-tree!DirTree/"
  (:require #?(:clj clojure.java.io)
            [dustingetz.str :refer [includes-str?]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]))

(e/defn Dir-tree* [handle search]
  (e/server
    (cond
      (.isDirectory handle)
      (e/client
        (dom/li (dom/text (e/server (.getName handle)))
          (dom/ul
            (e/server
              (let [children (.listFiles handle)]
                (e/for [handle (e/diff-by identity children)]
                  (Dir-tree* handle search)))))))

      (and (.isFile handle) (includes-str? (.getName handle) search))
      (e/client
        (dom/li (dom/text (e/server (.getName handle))))))))

(e/defn DirTree []
  (e/client
    (let [search (dom/input (dom/On "input" #(-> % .-target .-value) ""))
          handle (e/server (clojure.java.io/file "src/docs_site"))]
      (dom/ul
        (Dir-tree* handle search)))))