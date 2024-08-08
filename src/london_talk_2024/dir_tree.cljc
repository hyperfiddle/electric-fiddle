(ns london-talk-2024.dir-tree
  (:require
   [contrib.str :refer [includes-str?]]
   #?(:clj [clojure.java.io])
   [hyperfiddle.electric-de :as e :refer [$]]
   [hyperfiddle.electric-dom3 :as dom]))

; todo cannot inline interop - why not
#?(:clj (defn file-is-dir [h] (.isDirectory h)))
#?(:clj (defn file-is-file [h] (.isFile h)))
#?(:clj (defn file-list-files [h] (.listFiles h)))
#?(:clj (defn file-get-name [h] (.getName h)))
#?(:clj (defn file-absolute-path [^String path-str & more]
          (-> (java.nio.file.Path/of ^String path-str (into-array String more))
            .toAbsolutePath str)))

(e/defn Dir-tree [h s]
  (e/client
    (let [name_ (e/server (file-get-name h))]
      (cond
        (e/server (file-is-dir h))
        (dom/li (dom/text name_)
          (dom/ul
            (e/server
              (e/cursor [x (e/diff-by identity (file-list-files h))]
                ($ Dir-tree x s)))))

        (e/server (and (file-is-file h) (includes-str? name_ s)))
        (dom/li (dom/text name_))))))

(e/defn DirTreeDemo []
  (e/client
    (let [s (dom/input ($ dom/On "input" #(-> % .-target .-value) {} ""))
          h (e/server (clojure.java.io/file (file-absolute-path "./src/london_talk_2024")))]
      (dom/ul
        ($ Dir-tree h s)))))
