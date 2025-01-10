(ns electric-essay.read-src
  (:import (clojure.lang RT) (java.io InputStreamReader LineNumberReader PushbackReader))
  (:require [hyperfiddle.rcf :as rcf]
            [hyperfiddle.rcf :refer [tests]]
            [clojure.java.io :as io]))

(defn read-var-src
  "Returns a string of the source code for the given symbol, if it can find it. 
This requires that the symbol resolve to a Var defined in a namespace for which 
the .clj/cljs/cljc is in the classpath. Returns nil if it can't find the source.  
For most REPL usage, 'source' is more convenient. 

Example: (source-fn 'filter)"
  [x]
  (when-let [v (resolve x)]
    (when-let [filepath (:file (meta v))]
      (when-let [strm (.getResourceAsStream (RT/baseLoader) filepath)]
        (with-open [rdr (LineNumberReader. (InputStreamReader. strm))]
          (dotimes [_ (dec (:line (meta v)))] (.readLine rdr)) ; only start line is known
          (let [text (StringBuilder.)
                pbr (proxy [PushbackReader] [rdr]
                      (read []
                        (let [i (proxy-super read)] ; -1 signals eof
                          (case i -1
                            (throw (IllegalStateException. (format "read-src: src missing for var `%s, did you delete it?" x)))
                            (do (.append text (char i)) i))))) ; accumulate
                read-opts (if (.endsWith ^String filepath "cljc") {:read-cond :allow} {})] ; todo - find flag to read unresolved aliases?
            (if (= :unknown *read-eval*)
              (throw (IllegalStateException. "Unable to read source while *read-eval* is :unknown."))
              (read read-opts (PushbackReader. pbr))) ; read until form closes to infer end of fn src
            (str text)))))))

(defn read-var-src-safe [target] (try (read-var-src target) (catch Exception e (str e))))

(tests
  #_(read-var-src `electric-tutorial.input-zoo/Input*) ; works
  (read-var-src `first)
  := "(def
 ^{:arglists '([coll])
   :doc \"Returns the first item in the collection. Calls seq on its
    argument. If coll is nil, returns nil.\"
   :added \"1.0\"
   :static true}
 first (fn ^:static first [coll] (. clojure.lang.RT (first coll))))"
  #_(read-var-src 'electric-tutorial.forms3-crud/expand-tx-effects))

(defn resolve-var-or-ns [sym]
  ; careful, at times i've thought this impl unreliable but was usually user error related to REPL
  (if (qualified-symbol? sym)
    (resolve sym) (find-ns sym)))

(tests
  (-> (resolve-var-or-ns 'dev) meta :file) := "dev.cljc"
  (some? (-> (resolve-var-or-ns 'electric-essay.read-src/read-var-src) meta :file)) := true
  ; unreliable output?
  ; := "electric_essay/read_src.clj" "/Users/dustin/src/hf/electric-fiddle/src/electric_essay/read_src.clj"
  )

(defn -ns-resource-path [ns-sym extension] ; no cljc or cljs
  (-> ns-sym str (.replace \. \/) (.replace \- \_) (str extension)))

(tests
  (-ns-resource-path 'dev ".clj") := "dev.clj"
  (-ns-resource-path 'electric-essay.read-src ".clj") := "electric_essay/read_src.clj")

(defn read-ns-src-unreliable [ns-sym] ; careful - meta cannot always be relied on but seems preferable
  ; REPL namespaces don't have compiler meta
  ; iiuc, namespaces loaded by the electric shadow hook lose file metadata
  (let [?r (or
             (some-> ns-sym find-ns meta :file io/resource) ; 'dev
             (.getResource (clojure.lang.RT/baseLoader) (-ns-resource-path ns-sym ".clj"))
             (.getResource (clojure.lang.RT/baseLoader) (-ns-resource-path ns-sym ".cljc"))
             (.getResource (clojure.lang.RT/baseLoader) (-ns-resource-path ns-sym ".cljs")))]
    (some-> ?r slurp)))

(tests
  (-> (read-ns-src-unreliable 'dev) (subs 0 3)) := "(ns" ; first case
  (-> (read-ns-src-unreliable 'electric-essay.read-src) (subs 0 3)) := "(ns" ; second case
  )
