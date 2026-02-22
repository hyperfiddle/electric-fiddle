(ns dustingetz.nav-processes
  "macOS process table browser. Parses `ps aux` output."
  (:require
   [dustingetz.nav-util.shell :as shell]
   [clojure.string :as str]
   [hyperfiddle.hfql2 :as hfql :refer [hfql]]
   [hyperfiddle.hfql2.protocols :refer [Identifiable Suggestable -hfql-resolve]]))

;; -- Records --

(defrecord UnixProcess [pid user cpu mem vsz rss tty stat started time command])
(defrecord ProcessRef [pid])

;; -- Shell helpers --

(defn- parse-ps-line [line]
  (let [parts (str/split (str/trim line) #"\s+" 11)]
    (when (>= (count parts) 11)
      (let [[user pid cpu mem vsz rss tty stat started time & cmd] parts]
        (->UnixProcess pid user cpu mem vsz rss tty stat started time
          (str/join " " cmd))))))

;; -- Data functions --

(defn all-processes
  "All running processes from `ps aux` output."
  []
  (when-let [out (shell/sh-raw "ps" "aux")]
    (let [lines (rest (str/split-lines out))]
      (->> lines (map parse-ps-line) (remove nil?) vec))))

(defn top-cpu
  "Top 20 processes by CPU usage from `ps aux`."
  []
  (->> (all-processes)
    (sort-by #(- (Double/parseDouble (:cpu %))))
    (take 20) vec))

(defn top-mem
  "Top 20 processes by memory usage from `ps aux`."
  []
  (->> (all-processes)
    (sort-by #(- (Double/parseDouble (:mem %))))
    (take 20) vec))

(defn find-process
  "Single process detail by PID from `ps aux`."
  [pid]
  (first (filter #(= (str pid) (:pid %)) (all-processes))))

;; -- Named accessors --

(defn process-name [p]
  (let [cmd (:command p)]
    (last (str/split (first (str/split cmd #"\s")) #"/"))))
(defn process-pid [p] (:pid p))

;; -- Protocol extensions --

(extend-type ProcessRef
  Identifiable (-identify [r] `(find-process ~(:pid r)))
  Suggestable (-suggest [_] (hfql [:pid])))

(extend-type UnixProcess
  Identifiable (-identify [p] `(find-process ~(:pid p)))
  Suggestable (-suggest [_] (hfql [:pid :user :cpu :mem :vsz :rss :stat :command])))

;; -- Sitemap --

(def sitemap
  {`all-processes
   (hfql {(all-processes) {* ^{::hfql/select `find-process}
                              [^{::hfql/link `(find-process ~'%)} process-name
                               :pid :user :cpu :mem :stat]}})

   `top-cpu
   (hfql {(top-cpu) {* ^{::hfql/select `find-process}
                        [^{::hfql/link `(find-process ~'%)} process-name
                         :pid :cpu :mem :command]}})

   `top-mem
   (hfql {(top-mem) {* ^{::hfql/select `find-process}
                        [^{::hfql/link `(find-process ~'%)} process-name
                         :pid :cpu :mem :command]}})

   `find-process
   (hfql [:pid :user :cpu :mem :vsz :rss :tty :stat :started :time :command])})

;; -- Resolvers --

(defmethod -hfql-resolve `all-processes [_] (all-processes))
(defmethod -hfql-resolve `top-cpu [_] (top-cpu))
(defmethod -hfql-resolve `top-mem [_] (top-mem))
(defmethod -hfql-resolve `find-process [[_ pid]] (find-process pid))
