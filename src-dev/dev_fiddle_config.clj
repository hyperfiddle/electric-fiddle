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
  (let [config-file (io/file "electric-fiddle.edn")]
    (when (.exists config-file)
      (edn/read-string (slurp config-file)))))

(defn- domain-fiddle-ns [domain-sym]
  (symbol (str (name domain-sym) ".fiddles")))

(defn- domain-fiddle-routes [fiddle-sym]
  (list `hyperfiddle.electric3/call
    (symbol (str (domain-fiddle-ns fiddle-sym)) "Fiddles")))

(defn dev-fiddle-namespaces []
  (map domain-fiddle-ns (:loaded-fiddles (read-config))))

(defn dev-fiddle-routes []
  (map domain-fiddle-routes (:loaded-fiddles (read-config))))