(ns dustingetz.nav-py
  (:require
    [contrib.assert :refer [check]]
    #_dustingetz.pyhf ; see also
    [libpython-clj2.require :refer [require-python]]
    [libpython-clj2.python :refer [py. py.. py.-] :as py]))

#_(def py (delay ))
(require-python '[builtins :as python] #_#_'[os] '[platform])
(def os (time (py/import-module "os")))
(def platform (py/import-module "platform"))

(defn py-environ []
  (into {} (for [k (py.- os "environ")] ; different than py/dir
             [k (py. os "getenv" k)])))

(comment (py-environ))

(defn py-hello-world []
  (let [environ (py.- os "environ")]
    {::cpu-count (py. os "cpu_count")
     ::current-directory (py. os "getcwd")
     ::platform (get environ "PLATFORM")
     ::user (get environ "USER")
     ::path (py. os "getenv" "PATH") #_(py. os "path" "abspath" ".")
     #_#_::directory-contents (py. os "listdir" ".") ; List java.lang.String -- crashes navigator
     ::process-id (py. os "getpid")}))

(comment (py-hello-world))

(def cpu-count (fn [o] (py. o "cpu_count")))
(comment (cpu-count os))

(defn py-os [] os) ; easy entrypoint
(defn py-platform [] platform)

(type (py/module-dict platform))
(type (py/dir platform))

#_(defn dir [o] (for [x (py/dir o)] {::var x ::val (str (py. o "__getattribute__" x))}))
(defn dir [o] (into {} (for [x (py/dir o)] [x (str (py. o "__getattribute__" x))])))
