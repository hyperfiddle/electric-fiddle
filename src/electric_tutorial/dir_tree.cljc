(ns electric-tutorial.dir-tree
  (:require #?(:clj clojure.java.io)
            [dustingetz.str :refer [includes-str?]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]))

(e/defn Dir-tree* [h s]
  (e/server
    (let [name_ (.getName h)]
      (cond
        (.isDirectory h)
        (dom/li (dom/text name_)
          (dom/ul
            (e/for-by hash [x (.listFiles h)]
              (Dir-tree* x s))))

        (and (.isFile h) (includes-str? name_ s))
        (dom/li (dom/text name_))))))

(e/defn DirTree []
  (e/client
    (let [s (dom/input (dom/On "input" #(-> % .-target .-value) ""))
          h (e/server (-> "./vendor/electric/src/hyperfiddle"
                        (java.nio.file.Path/of (into-array String []))
                        .toAbsolutePath str clojure.java.io/file))]
      (dom/ul
        (Dir-tree* h s)))))