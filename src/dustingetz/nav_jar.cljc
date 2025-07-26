(ns ^{:hyperfiddle.electric.impl.lang3/has-edef? true} ; enable server hot reloading
  dustingetz.nav-jar
  #?(:clj (:import [java.util.jar JarFile JarEntry]))
  (:require
    #?(:clj [dustingetz.fs2 :refer [path-filename]])
    [hyperfiddle.hfql0 #?(:clj :as :cljs :as-alias) hfql]))

#?(:clj
   (do

     (defn list-project-jars []
       (let [cp (System/getProperty "java.class.path")]
         (->> (clojure.string/split cp #"[;:]")
           (filter #(.endsWith % ".jar"))
           (map #(JarFile. (clojure.java.io/file %))))))

     (defn jar-entries [!jar] (enumeration-seq (.entries !jar)))
     (defn jar-manifest [!jar] (some->> !jar .getManifest .getMainAttributes .entrySet seq (into {})))
     (defn jar-filename [!jar] (path-filename (.getName !jar)))
     (defn jar-jmanifest-entries [!jar-manifest] (into {} (seq (.entrySet !jar-manifest))))

     (def sitemap
       (hfql/sitemap
         {list-project-jars [jar-filename]}))

     (extend-protocol hfql/Suggestable
       JarFile (-suggest [_] (hfql/pull-spec [.getName jar-filename jar-manifest .getVersion jar-entries type]))
       java.util.jar.Manifest (-suggest [_] (hfql/pull-spec [.getMainAttributes #_.getEntries type]))
       java.util.jar.JarFile$JarFileEntry (-suggest [_] (hfql/pull-spec [.getName .getSize type]))
       java.util.jar.Attributes (-suggest [_] (hfql/pull-spec [.values jar-jmanifest-entries type])))

     ))