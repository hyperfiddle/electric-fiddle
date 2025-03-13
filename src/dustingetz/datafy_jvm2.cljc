(ns dustingetz.datafy-jvm2
  (:import [java.lang.management ManagementFactory]
           java.lang.management.ThreadInfo
           com.sun.management.ThreadMXBean)
  (:require [clojure.core.protocols :refer [Datafiable nav] :rename {nav -nav}]
            [hyperfiddle.nav0 :refer [Identifiable -identify]]))

(defn resolve-thread [id] (apply com.sun.management.ThreadMXBean/.getThreadInfo
                            (ManagementFactory/getThreadMXBean) [id]))

(defn resolve-thread-manager [] (ManagementFactory/getThreadMXBean))

(defn resolve-class [whiteset qs] (try (some-> (whiteset qs) resolve) (catch Exception e nil)))

(defn thread-cpu-time [^java.lang.management.ThreadInfo x]
  (.getThreadCpuTime (ManagementFactory/getThreadMXBean) x))

(defn threadmx-dump-all-threads [^com.sun.management.ThreadMXBean x]
  (->> (.dumpAllThreads x true true) ; how to hydrate automatically?
    (sort-by #(- (thread-cpu-time (.getThreadId %)))) vec))

(defn threadmx-get-all-thread-ids [^com.sun.management.ThreadMXBean x]
  (with-meta
    (seq (.getAllThreadIds x))
    {#_#_`-identify identity
     `-nav (fn [xs k v] (.getThreadInfo x v))}))

(comment
  (require '[clojure.datafy :refer [datafy nav]]
    '[dustingetz.hfql11 :refer [hfql hfql-search-sort]])
  (def x (ManagementFactory/getThreadMXBean))
  (time (count (->> (threadmx-get-all-thread-ids x) (hfql-search-sort {} ['*] "")))) := _ ; ~150
  (time (count (->> (threadmx-get-all-thread-ids x) (hfql-search-sort {} ['*] "TIMED_WAITING")))) := _ ; ~33

  (->> (threadmx-get-all-thread-ids x)
    (hfql-search-sort {} [`type] "ThreadInfo"))

  (def as (threadmx-get-all-thread-ids x))
  (count as)
  (def a (nav as 0 (nth as 0)))
  (type a) := java.lang.management.ThreadInfo
  (hfql [`type] a)
  (hfql [`threadinfo-name
         `threadinfo-state
         `threadinfo-id
         `threadinfo-stacktrace
         `threadinfo-blocked-time
         `threadinfo-waited-time
         `threadinfo-lock-info
         `threadinfo-cpu-time] a)

  (datafy a)

  (type (threadinfo-state a))
  )

(extend-type com.sun.management.ThreadMXBean
  Identifiable (-identify [^com.sun.management.ThreadMXBean x] (-> x .getObjectName str))
  Datafiable
  (datafy [^com.sun.management.ThreadMXBean x]
    (-> {::object-name (str (.getObjectName x))
         ::thread-count (.getThreadCount x)
         ::dumpAllThreads #(threadmx-dump-all-threads x) ; hyperlink
         ::getAllThreadIds #(threadmx-get-all-thread-ids x) #_#(->> (threadmx-get-all-thread-ids x) (mapv (partial hash-map ::thread-id)))
         ::deadlocked-threads (vec (.findDeadlockedThreads x))
         #_#_::isThreadCpuTimeSupported (.isThreadCpuTimeSupported x)
         #_#_::setThreadCpuTimeEnabled #(.setThreadCpuTimeEnabled x true)}
      (with-meta
        {`-nav
         (fn [o k v]
           (case k
             ::dumpAllThreads (if v (v))
             ::getAllThreadIds (if v (v))
             #_#_::setThreadCpuTimeEnabled (if v (v))
             v))}))))

(defn threadinfo-name [^java.lang.management.ThreadInfo x] (.getThreadName x))
(defn threadinfo-state [^java.lang.management.ThreadInfo x] (.getThreadState x))
(defn threadinfo-id [^java.lang.management.ThreadInfo x] (.getThreadId x))
(defn threadinfo-stacktrace [^java.lang.management.ThreadInfo x] (seq (.getStackTrace x)))
(defn threadinfo-blocked-time [^java.lang.management.ThreadInfo x] (.getBlockedTime x))
(defn threadinfo-waited-time [^java.lang.management.ThreadInfo x] (.getWaitedTime x))
(defn threadinfo-lock-info [^java.lang.management.ThreadInfo x] (.getLockInfo x))
(defn threadinfo-cpu-time [^java.lang.management.ThreadInfo x] (.getThreadCpuTime (ManagementFactory/getThreadMXBean) (.getThreadId x))) ; ?

(extend-type java.lang.management.ThreadInfo
  Identifiable (-identify [^java.lang.management.ThreadInfo x] (.getThreadId x))
  Datafiable
  (datafy [^java.lang.management.ThreadInfo x]
    (-> {::name (threadinfo-name x)
         ::state #_(str) (threadinfo-state x)
         ::thread-id (threadinfo-id x)
         ::stack-trace #(vec (threadinfo-stacktrace x))
         ::blocked-time (threadinfo-blocked-time x)
         ::waited-time (threadinfo-waited-time x)
         ;::toString (.toString x) ; visualvm dump
         ::lock-info (threadinfo-lock-info x)
         ::cpu-time (threadinfo-cpu-time x)}
      (with-meta
        {`-nav
         (fn [o k v]
           (case k
             ::stack-trace (if v (v))
             ;::toString (if v (v))
             v))}))))

(extend-type java.lang.management.ThreadInfo/1
  Identifiable (-identify [x] (assert false (str "-identify unimplemented, x: " x))) ; why is this implemented at all? Isn't it because default impl for Identifiable is `identity`?
  Datafiable (datafy [x] (vec x)))

(extend-type java.lang.StackTraceElement
  Identifiable (-identify [^java.lang.StackTraceElement x] (hash x)) ; better to use local index fallback
  Datafiable (datafy [^java.lang.StackTraceElement x] {::toString (.toString x)})) ; members are private
