(ns electric-tutorial.dir-tree
  (:require #?(:clj clojure.java.io)
            [contrib.str :refer [includes-str?]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]))

#?(:clj (defn file-is-dir [h] (.isDirectory h)))
#?(:clj (defn file-is-file [h] (.isFile h)))
#?(:clj (defn file-list-files [h] (.listFiles h)))
#?(:clj (defn file-get-name [h] (.getName h)))
#?(:clj (defn file-absolute-path [^String path-str & more]
          (-> (java.nio.file.Path/of ^String path-str (into-array String more))
            .toAbsolutePath str)))

(e/defn Dir-tree* [h s]
  (e/server
    (let [name_ (file-get-name h)]
      (cond
        (file-is-dir h)
        (dom/li (dom/text name_)
          (dom/ul
            (e/for-by hash [x (file-list-files h)]
              (Dir-tree* x s))))

        (and (file-is-file h) (includes-str? name_ s))
        (dom/li (dom/text name_))))))

(e/defn DirTree []
  (e/server
    (let [s (e/client (dom/input (dom/On "input" #(-> % .-target .-value) "")))
          h (clojure.java.io/file (file-absolute-path "./vendor/electric/src/hyperfiddle"))]
      (dom/ul
        (Dir-tree* h s)))))
