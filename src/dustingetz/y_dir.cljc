(ns dustingetz.y-dir
  (:require [contrib.str :refer [includes-str?]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms3 :refer [Input*]]))

(e/defn Y [Gen]
  (e/call
    (e/fn   [F] (F F)) ; call-with-self
    (e/fn F [F] (Gen (e/fn Recur [x]
                       (e/call (F F) x))))))

(e/defn Dir-tree [Recur]
  (e/fn [[h s]]
    (let [name_ (.getName h)]
      (cond
        (.isDirectory h)
        (dom/li (dom/text name_)
          (dom/ul
            (e/for [x (e/diff-by hash (.listFiles h))]
              (Recur [x s])))) ; recur

        (and (.isFile h) (includes-str? name_ s))
        (dom/li (dom/text name_))))))

(e/defn Y-dir []
  (e/server
    (let [s (e/client (Input* "")) ; e/client is redundant
          h (-> "./src/dustingetz"
              (java.nio.file.Path/of (into-array String []))
              .toAbsolutePath str clojure.java.io/file)]
      (dom/ul
        (e/call (Y Dir-tree) [h s])))))
