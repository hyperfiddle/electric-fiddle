(ns dustingetz.learn-hfql-protocols-scratch
  (:require
   [hyperfiddle.hfql2 :as hfql :refer [hfql]]
   [hyperfiddle.hfql2.protocols :refer [Identifiable hfql-resolve Navigable Suggestable ComparableRepresentation]]))

(set! clojure.core/*print-namespace-maps* false) ; better REPL print indentation

#?(:clj (extend-type java.io.File
          Identifiable (identify [^java.io.File o] `(clojure.java.io/file ~(.getPath o)))
          Suggestable (suggest [o] (hfql [java.io.File/.getName
                                          java.io.File/.getPath
                                          java.io.File/.getAbsolutePath
                                          {java.io.File/.listFiles {* ...}}]))))

#?(:clj (defmethod hfql-resolve 'clojure.java.io/file [[_ file-path-str]] (clojure.java.io/file file-path-str)))

#?(:clj (extend-type clojure.lang.Namespace
          Identifiable (identify [ns] `(find-ns ~(ns-name ns)))
          Suggestable (suggest [_] (hfql [ns-name ns-publics meta]))))

#?(:clj (defmethod hfql-resolve `find-ns [[_ ns-sym]] (find-ns ns-sym)))

#?(:clj (extend-type clojure.lang.Var
          Identifiable (identify [ns] `(find-var ~(symbol ns)))
          Suggestable (suggest [_] (hfql [symbol meta .isMacro .isDynamic .getTag]))))

#?(:clj (defmethod hfql-resolve `find-var [[_ var-sym]] (find-var var-sym)))

(comment
  (def !x (clojure.java.io/file "../hyperfiddle/src"))
  (time (hfql/pull (hfql {!x [java.io.File/.getName
                              java.io.File/.getAbsolutePath
                              type]})))
    ; "Elapsed time: 0.904416 msecs"
  := '{!x
       {java.io.File/.getName "src",
        java.io.File/.getAbsolutePath "/Users/dustin/src/hf/monorepo/datomic-browser/../hyperfiddle/src",
        type java.io.File}}

  (time (hfql/pull (hfql {(clojure.java.io/file ".")
                          [java.io.File/.getName
                           {java.io.File/.listFiles {* 2}}]})))

  (time (hfql/pull (hfql {(all-ns)
                          {* ; cardinality many (no datomic schema available)
                           [ns-name type]}})))
  (count (first (vals *1))) := 622)

(comment
  {'slow-query
   (hfql {(slow-query)
          [*]})

   'file
   (hfql {(clojure.java.io/file ".")
          [java.io.File/.getName
           {java.io.File/.listFiles {* ...}}]})

   'all-ns
   (hfql {(all-ns)
          {* [ns-name
              meta
              {ns-publics {vals {* [str meta]}}}]}})})

(comment
  (defn paginate [f limit sort-key]
    (take limit (sort-by sort-key (f))))

  (hfql/pull
   (hfql {(paginate ^:symbolic all-ns 5 ^:symbolic ns-name)
          {* [ns-name type]}}))

  (hfql/pull
   (hfql {(take 5 ^:symbolic (sort-by ns-name (all-ns)))
          {* [ns-name type]}}))

  (def q (hfql {(all-ns)
                {* [ns-name type]}}))
  (as-> (hfql/pull q) $
    (get $ '(all-ns))
    (sort-by 'ns-name $)
    (take 5 $))

  (->> (all-ns) (sort-by ns-name) (take 5)))