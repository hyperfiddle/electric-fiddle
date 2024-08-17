(ns london-talk-2024.dir-tree
  (:require
   [contrib.str :refer [includes-str?]]
   #?(:clj [clojure.java.io])
   [hyperfiddle.electric-de :as e :refer [$]]
   [hyperfiddle.electric-dom3 :as dom]))

#?(:clj (defn file-is-dir [h] (.isDirectory h)))
#?(:clj (defn file-is-file [h] (.isFile h)))
#?(:clj (defn file-list-files [h] (.listFiles h)))
#?(:clj (defn file-get-name [h] (.getName h)))
#?(:clj (defn file-absolute-path [^String path-str & more]
          (-> (java.nio.file.Path/of ^String path-str (into-array String more))
            .toAbsolutePath str)))

(e/defn Dir-tree [h s]
  (e/server
    (let [name_ (file-get-name h)]
      (cond
        (file-is-dir h)
        (e/client
          (dom/li (dom/text name_)
            (dom/ul
              (e/server
                (e/for-by identity [x (file-list-files h)]
                  ($ Dir-tree x s))))))

        (e/server (and (file-is-file h) (includes-str? name_ s)))
        (e/client (dom/li (dom/text name_)))))))

(e/defn DirTreeDemo []
  (e/client
    (let [s (dom/input ($ dom/On "input" #(-> % .-target .-value) ""))
          h (e/server (clojure.java.io/file (file-absolute-path "./vendor/electric/src/hyperfiddle")))]
      (dom/ul
        ($ Dir-tree h s)))))
