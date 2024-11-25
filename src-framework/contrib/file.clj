(ns contrib.file
  (:require [clojure.java.io :as io]))

(defn ensure-file [filepath]
  (let [file (io/file filepath)]
    (io/make-parents file)
    (when-not (.exists file) (.createNewFile file))
    file))