(ns dustingetz.nav-jar
  (:import
   [java.io File]
   [java.util.jar JarFile JarEntry])
  (:require
   [contrib.assert :refer [check]]
   [hyperfiddle.hfql2 :as hfql :refer [hfql]]
   [hyperfiddle.hfql2.protocols :as hfqlp]))

(defn jar-by-path [jar-path-str] (JarFile. (clojure.java.io/file jar-path-str)))

(defn list-project-jars []
  (let [cp (System/getProperty "java.class.path")]
    (->> (clojure.string/split cp #"[;:]")
      (filter #(.endsWith % ".jar"))
      (map jar-by-path))))

(defn path-filename [filepath-str]
  (when filepath-str (.getName (check (File. filepath-str))))) ; doesn't have to exist

(defn jar-entries [!jar] (.entries !jar))
(defn jar-manifest [!jar] (some->> !jar .getManifest .getMainAttributes .entrySet seq (into {})))
(defn jar-filename [!jar] (path-filename (.getName !jar)))
(defn jar-jmanifest-entries [!jar-manifest] (into {} (seq (.entrySet !jar-manifest))))

(extend-type java.util.jar.JarFile
  hfqlp/Identifiable (-identify [x] `(jar-by-path ~(.getName x)))
  hfqlp/Suggestable (-suggest [_] (hfql [.getName
                                         jar-filename
                                         jar-manifest
                                         {.getVersion [.major .minor .feature]}
                                         jar-entries])))

(defmethod hfqlp/-hfql-resolve `jar-by-path [[_ jar-path-str]] (jar-by-path jar-path-str))

(extend-type java.util.jar.JarFile$JarFileEntry
  ;; hfqlp/Identifiable (-identify [x] ? ) ; FIXME Identity is (jar-path × entry name) but not clear how to refer to the selected parent jar in context.
  hfqlp/Suggestable (-suggest [_] (hfql [.getName
                                         .getSize
                                         .getCompressedSize
                                         type])))

(extend-type java.util.jar.Manifest
  hfqlp/Suggestable (-suggest [_] (hfql [.getMainAttributes type])))

(extend-type java.util.jar.Attributes
  hfqlp/Suggestable (-suggest [_] (hfql [.values jar-jmanifest-entries type])))

(extend-type java.util.jar.Attributes$Name
  hfqlp/Identifiable (-identify [obj] `(jar-attribute-name ~(str obj)))
  hfqlp/Suggestable (-suggest [_] (hfql [.toString])))

(defmethod hfqlp/-hfql-resolve `jar-attribute-name [[_ jar-attribute-name-str]] (new java.util.jar.Attributes$Name jar-attribute-name-str))

(def sitemap
  {`jar (hfql {(list-project-jars) {* [*]}})})

