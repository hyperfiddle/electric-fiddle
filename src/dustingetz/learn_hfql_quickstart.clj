(ns dustingetz.learn-hfql-quickstart
  (:require
   [hyperfiddle.hfql2 :as hfql :refer [hfql]]
   [hyperfiddle.hfql2.protocols :refer [Identifiable Suggestable]]
   [hyperfiddle.rcf :refer [tests]]))

(set! clojure.core/*print-namespace-maps* false) ; better REPL print indentation
(hyperfiddle.rcf/enable!)

; HFQL is generalized Datomic pull over any object
; Let's start with a file

(tests
  "HFQL hello world: java.io.File"
  (def q (hfql {(clojure.java.io/file ".")
                [java.io.File/.getName
                 java.io.File/.getAbsolutePath
                 type]}))
  (hfql/pull q) ; run the query
  := {'(clojure.java.io/file ".")
      {'java.io.File/.getName ".",
       'java.io.File/.getAbsolutePath "/Users/dustin/src/hf/monorepo/electric-fiddle/.",
       'type java.io.File}}

 ; HFQL results travel with their semantic name, here '(clojure.java.io/file ".")
 ; HFQL results are named by a semantic/symbolic constructor expression
 ; We wll later use this name in routing

  (def !x (clojure.java.io/file "."))
  (def q (hfql {!x
                [java.io.File/.getName
                 java.io.File/.getAbsolutePath
                 type]}))
  (hfql/pull q)
  := {'!x
      {'java.io.File/.getName ".",
       'java.io.File/.getAbsolutePath "/Users/dustin/src/hf/monorepo/electric-fiddle/.",
       'type java.io.File}})

(tests
  "HFQL recursive pull"
  ; List of files
  (def q (hfql {!x [java.io.File/.getName
                    {java.io.File/.listFiles {* 1}}]})) ; recursive pull syntax
  (time (hfql/pull q)) ; "Elapsed time: 0.856208 msecs"

  (def q (hfql {(clojure.java.io/file ".")
                [java.io.File/.getName
                 {java.io.File/.listFiles {* ...}}]})) ; infinite pull
  (def xs (time (vec (hfql/pull q)))) ; "Elapsed time: 0.193417 msecs"
  (type xs) := clojure.lang.PersistentVector
  (type (first xs)) := clojure.lang.MapEntry
  (first (first xs)) := '(clojure.java.io/file ".")
  #_(clojure.pprint/pprint xs) ; todo can it work?
  #_xs ; Error printing return value (StackOverflowError)
  )

(tests
 "HFQL cardinality many"
 (def xs (take 3 (all-ns)))
 (def q (hfql {xs {* [type]}})) ; * means foreach
 (hfql/pull q)
 := {'!xs [{'type clojure.lang.Namespace}
           {'type clojure.lang.Namespace}
           {'type clojure.lang.Namespace}]}

 ; in Datomic pull, the * is implicit from the cardinality of the schema attribute
 ; Arbitrary objects do not have schema, so the programmer must inject that information

 ; We can also query the seq object, which is typically not what you want but
 ; it is well defined. Note here we pull `count ` an aggregating fn.
 (def q (hfql {xs [type count]})) ; no *, so we see the collection, not each element
 (hfql/pull q)
 := {'xs {'type clojure.lang.LazySeq, 'count 3}})


; Ok lets query some progressively more interesting objects

(tests
 "HFQL over clojure.deps"
 (require '[clojure.tools.deps :as deps])
 (def x (deps/slurp-deps (clojure.java.io/file "deps.edn"))) ; a deep EDN document
 (hfql/pull (hfql {x [{:aliases [count keys]}]}))
 := {'x
     {:deps
      {'count 10,
       'keys
       '[org.clojure/clojure
         org.clojure/tools.logging
         com.hyperfiddle/electric
         ring-basic-authentication/ring-basic-authentication
         org.clojure/clojurescript
         com.hyperfiddle/hyperfiddle-contrib
         com.hyperfiddle/rcf
         com.hyperfiddle/hyperfiddle
         ch.qos.logback/logback-classic
         ring/ring-core]}}})

(tests
 "HFQL over Java"
 (import '[java.lang.management ManagementFactory])
 (def os (ManagementFactory/getOperatingSystemMXBean))
 (hfql/pull (hfql {os [.getArch .getAvailableProcessors .getCpuLoad .getSystemCpuLoad type]}))
 := {'os
     {'.getArch "aarch64",
      '.getAvailableProcessors 20,
      '.getCpuLoad _,
      '.getSystemCpuLoad _,
      'type com.sun.management.internal.OperatingSystemImpl}})

(comment
  "HFQL over AWS"
  (require '[clojure.repl.deps :refer [add-lib]])
  (add-lib 'com.cognitect.aws/api)
  (require '[cognitect.aws.client.api :as aws])
  (def !s3 (aws/client {:api :s3 :region "us-east-1"})) ; requires aws login
  (hfql/pull (hfql {(aws/invoke !s3 {:op :ListBuckets}) [{:Buckets {* [:Name]}}]}))
  (def s3-index (clojure.datafy/datafy !s3))
  (keys s3-index)
  (hfql/pull (hfql {s3-index [:api :region {:ops keys}]})) ; inline fn application at leaf
  )

(comment
  "HFQL over jar files"
  (import '[java.util.jar JarFile JarEntry])
  (defn list-project-jars []
    (let [cp (System/getProperty "java.class.path")]
      (->> (clojure.string/split cp #"[;:]")
        (filter #(.endsWith % ".jar"))
        (map #(JarFile. (clojure.java.io/file %))))))

  (hfql/pull
    (hfql {(list-project-jars)
           {* [str
               .getName
               .getVersion
               .getManifest]}}))

  (def xs (->> (list-project-jars)
            (filter #(clojure.string/includes? (str %) "clojure"))))
  (hfql/pull (hfql {xs {* [.getName {.entries enumeration-seq}]}})) ; todo HFQL should understand java.util.Enumeration natively
  )

(tests
  "HFQL big complicated nested pull"
  ; More complicated object, this one is a collection represented as an indexed map
  (def xs (ns-publics (find-ns 'clojure.core))) ; returns a REPL-friendly indexed map, not a list
  (hfql/pull (hfql {xs {* [type {val [type str]}]}})) ; iterate the map's mapentries
  (hfql/pull (hfql {xs {vals {* [type str]}}})) ; iterate the map's vals

  ; bigger still
  (def xs (->> (all-ns) (filter #(clojure.string/starts-with? (ns-name %) "clojure"))))
  (count xs) := 120
  (defn var-arglists [!var] (->> !var meta :arglists seq pr-str))
  (def q (hfql {xs
                {* [ns-name
                    {meta [:doc]}
                    {ns-publics {vals {* [str
                                          {meta [:doc]}
                                          var-arglists]}}}]}}))
  (time (hfql/pull q)))
