(ns config
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]))

(defn read-config []
  (let [config-file (io/file "electric-fiddle.edn")]
    (when (.exists config-file)
      (edn/read-string (slurp config-file)))))

(defn fiddle-entrypoint-ns [fiddle-sym] (symbol (str (name fiddle-sym) ".fiddles")))
(defn fiddle-entrypoint [fiddle-sym] (list `hyperfiddle.electric3/call (symbol (str (fiddle-entrypoint-ns fiddle-sym)) "Fiddles")))

(defn loaded-fiddles "Return a list symbols, representing currently loaded fiddles' namespaces"
  []
  (map fiddle-entrypoint-ns (:loaded-fiddles (read-config))))

(defn loaded-fiddles-entrypoints "Return a list symbols, representing currently loaded fiddles' entrypoints."
  []
  (map fiddle-entrypoint (:loaded-fiddles (read-config))))
