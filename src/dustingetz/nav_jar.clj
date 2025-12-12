(ns dustingetz.nav-jar
  (:import
   [java.io File]
   [java.util.jar JarFile JarEntry])
  (:require
   [contrib.assert :refer [check]]
   [hyperfiddle.hfql2 :as hfql :refer [hfql]]
   [hyperfiddle.hfql2.protocols :as hfqlp]))

(defn list-project-jars []
  (let [cp (System/getProperty "java.class.path")]
    (->> (clojure.string/split cp #"[;:]")
      (filter #(.endsWith % ".jar"))
      (map #(JarFile. (clojure.java.io/file %))))))

(defn path-filename [filepath-str]
  (when filepath-str (.getName (check (File. filepath-str))))) ; doesn't have to exist

(defn jar-entries [!jar] (enumeration-seq (.entries !jar)))
(defn jar-manifest [!jar] (some->> !jar .getManifest .getMainAttributes .entrySet seq (into {})))
(defn jar-filename [!jar] (path-filename (.getName !jar)))
(defn jar-jmanifest-entries [!jar-manifest] (into {} (seq (.entrySet !jar-manifest))))

(extend-type java.util.jar.JarFile$JarFileEntry
  hfqlp/Identifiable (-identify [x] (-> x str))  ; wrong impl, must be replaced by a symbolic constructor call
  hfqlp/Suggestable (-suggest [_] (hfql [.getName
                                         .getSize
                                         .getCompressedSize
                                         type])))

(extend-type java.util.jar.JarFile
  hfqlp/Identifiable (-identify [x] `(jar-by-path ~(.getName x)))
  hfqlp/Suggestable (-suggest [_] (hfql [.getName
                                         jar-filename
                                         jar-manifest
                                         {.getVersion [.major .minor .feature]}
                                         jar-entries])))

(extend-type java.util.jar.Manifest
  hfqlp/Suggestable (-suggest [_] (hfql [.getMainAttributes #_.getEntries type])))

(extend-type java.util.jar.JarFile$JarFileEntry
  hfqlp/Suggestable (-suggest [_] (hfql [.getName .getSize type])))

(extend-type java.util.jar.Attributes
  hfqlp/Suggestable (-suggest [_] (hfql [.values jar-jmanifest-entries type])))

;; TODO: Crashes
;; #?(:clj (extend-type java.util.jar.Attributes$Name
;;           hfqlp/Identifiable (-identify [x] (str x))))

(extend-type java.util.jar.Attributes$Name ; TODO implement Identifiable as a symbolic constructor
  hfqlp/Suggestable (-suggest [_] (hfql [.toString])))

(defmethod hfqlp/-hfql-resolve 'dustingetz.nav-jar/jar-by-path [[_ jar-path]]
  (JarFile. (clojure.java.io/file jar-path)))

(def sitemap
  {`jar (hfql {(list-project-jars) {* [*]}})})