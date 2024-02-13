(ns fiddle-manager
  (:require
   [clojure.java.io :as io]
   [clojure.tools.deps :as deps]
   [clojure.tools.logging :as log]
   [hyperfiddle.electric :as e]
   [hyperfiddle.electric-local-def :as local]
   [hyperfiddle.rcf :as rcf])
  (:import
   (hyperfiddle.electric Pending)
   (missionary Cancelled)))

(defonce ^{:doc "Contains the set of loaded fiddles. A set of namespace symbols."}
  !FIDDLES
  (atom #{}))

(defn fiddle-entrypoint-ns [fiddle-sym] (symbol (str (name fiddle-sym) ".fiddles")))
(defn fiddle-entrypoint [fiddle-sym] (symbol (str (fiddle-entrypoint-ns fiddle-sym)) "fiddles"))

(defn loaded-fiddles "Return a list symbols, representing currently loaded fiddles' namespaces"
  []
  (map fiddle-entrypoint-ns @!FIDDLES))

(defn loaded-fiddles-entrypoints "Return a list symbols, representing currently loaded fiddles' entrypoints."
  []
  (map fiddle-entrypoint @!FIDDLES))

(defn load-fiddle! [& ns-syms]
  (assert (every? simple-symbol? ns-syms))
  (swap! !FIDDLES into ns-syms))

(defn unload-fiddle! [ns-sym] (swap! !FIDDLES disj ns-sym))

(defn fiddle-extra-deps [fiddle-ns-sym]
  (when-let [deps (deps/slurp-deps (io/file "deps.edn"))]
    (some-> deps :aliases (get (keyword fiddle-ns-sym)) :extra-deps)))

(defn explain-error [fiddle error]
  (cond
    (instance? java.io.FileNotFoundException error)
    (str
      "\nPossible causes:"
      "\n - this fiddle doesn’t exist"
      "\n   - is `" fiddle "` the right name?"
      "\n   - is there a corresponding file named `src/" (munge fiddle) "/fiddles.cljc`?"
      )
    (instance? java.io.FileNotFoundException (ex-cause error))
    (str
      "\nWe could find a fiddle named `" fiddle "` but failed to load it."
      "\nPossible causes:"
      "\n - there’s a typo in the `ns` form"
      "\n - a dependency is missing:"
      (if-let [deps (seq (fiddle-extra-deps fiddle))]
        (let [cnt (count deps)]
          (str
            "\n   - there " (if (> cnt 1) "are" "is") " " cnt " extra dependencies for `"fiddle"` in `deps.edn`: " (mapv first deps)
            "\n     - is the missing dependency listed? If no you should add it to `deps.edn` under `:aliases` -> `" (keyword fiddle)"` -> `:extra-deps`."
            "\n     - did you start your REPL with the `" (keyword fiddle) "` alias?"
            ))
        (str "\n   - are you using an external library?"
             "\n     If so, you need to:"
             "\n      1. add an alias for " (keyword fiddle) " in `deps.edn`"
             "\n      2. list your dependency under `:extra-deps`"
             "\n      3. restart your REPL, adding the `"(keyword fiddle)"` alias.")))
    :else error))

(defn require-fiddle [fiddle]
  (let [ns-sym (fiddle-entrypoint-ns fiddle)]
    (println "Loading fiddle:" fiddle)
    (let [rcf-state rcf/*enabled*]
      (try (rcf/enable! false)
           (require ns-sym :reload)
           (println "Loaded:" ns-sym)
           (find-ns ns-sym)
           (catch Throwable t
             (println)
             (log/error "failed to load fiddle" (str "`" fiddle "`")
               (str "\n" (ex-message t))
               (when-let [cause (ex-cause t)]
                 (str "\n" (ex-message cause)))
               (explain-error fiddle t))
             (unload-fiddle! fiddle)
             nil)
           (finally
             (rcf/enable! rcf-state))))))

(defn touch! "Create or mark a file as modified." [file-or-filepath]
  (let [file (io/as-file file-or-filepath)]
    (if-not (.exists file)
      (.createNewFile file)
      (.setLastModified file (System/currentTimeMillis)))))

(local/defn FiddleLoader [path fiddles]
  (e/server
    (let [loaded-fiddles (e/for-by identity [fiddle fiddles]
                           (try
                             (e/offload #(require-fiddle fiddle))
                             (e/on-unmount #(do (println "Unloading fiddle:" fiddle)
                                                (remove-ns (fiddle-entrypoint-ns fiddle))))
                             fiddle
                             (catch hyperfiddle.electric.Pending _
                               false)))]
      (when (not-any? false? loaded-fiddles)
        ((fn [_] (touch! path)) loaded-fiddles))) ; rerun fn whenever `loaded-fiddles` changes
    (e/on-unmount #(touch! path))))


(local/defn FiddleManager [{:keys [loader-path] :as _config}]
  (try
    (e/server
      (let [fiddles (e/watch !FIDDLES)]
        (FiddleLoader. loader-path fiddles)))
    (catch Pending _)
    (catch Cancelled _)
    (catch Throwable t
      (prn "Fiddle Manager crashed" t))))

(def manager nil)

(def DEFAULT-CONFIG {:loader-path "src-dev/fiddles.cljc"})

(defn start!
  ([] (start! nil))
  ([config]
   (when (nil? manager)
     (def manager (local/run (FiddleManager. config))))))

(defn stop! []
  (when (some? manager)
    (manager)
    (def manager nil)))

(comment
  (start!)
  (load-fiddle! 'hello-fiddle)
  (unload-fiddle! 'hello-fiddle)
  (load-fiddle! 'i-dont-exist)
  (stop!)
  )

;; Experimental: load fiddle deps at runtime
;; (defn add-libs-for-fiddle [fiddle-ns-sym]
;;   (when-let [extra-deps (fiddle-extra-deps fiddle-ns-sym)]
;;     (when-let [added-libs (binding [*repl* true] (clojure.repl.deps/add-libs extra-deps))]
;;       (println "Those libraries were loaded on demand:" (keys extra-deps)))))
