(ns dev-fiddle-config
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]))

; electric-fiddle.edn contains a list of fiddle domains.
; Fiddle domains are:
; 1. a top level directory `dustingetz`
; 2. containing a file `fiddles.cljc` which export two things
;   - a Fn (e/defn Fiddles [] {`Hello Hello}) -- returning a map of e/fn names to their impl
;   - a prod entrypoint, i.e. that elides the dev scaffolding electric-fiddle.fiddle-index/FiddleRoot

(defn- read-config []
  (let [config-file (io/file "src-dev/electric-fiddle.edn")] ; io/resource doesn't work at read time
    (when (.exists config-file)
      (edn/read-string (slurp config-file)))))

(defn dev-fiddle-namespaces []
  (:hyperfiddle/fiddles (read-config)))

(defn dev-comptime-fiddle-indexes []
  (->> (:hyperfiddle/fiddles (read-config))
    (mapv (fn [fiddle-ns-sym]
            (list `hyperfiddle.electric3/call
              (symbol (name fiddle-ns-sym) "Fiddles"))))))

(comment
  (dev-comptime-fiddle-indexes) :=
  [(hyperfiddle.electric3/call hello-fiddle.hello-fiddle/Fiddles)
   (hyperfiddle.electric3/call electric-tutorial.fiddles/Fiddles)
   (hyperfiddle.electric3/call dustingetz.fiddles/Fiddles)])