(ns dustingetz.datafy-jvm2
  (:import [java.lang.management ManagementFactory]
           java.lang.management.ThreadInfo
           com.sun.management.ThreadMXBean)
  (:require clojure.core.protocols))

(defn resolve-thread [id] (apply com.sun.management.ThreadMXBean/.getThreadInfo
                            (ManagementFactory/getThreadMXBean) [id]))

(defn resolve-thread-manager [] (ManagementFactory/getThreadMXBean))

(extend-protocol clojure.core.protocols/Datafiable
  com.sun.management.ThreadMXBean
  (datafy [x]
    (-> {::object-name (str (.getObjectName x))
         ::thread-count (.getThreadCount x)
         ::dumpAllThreads #(->> (.dumpAllThreads x true true) ; hyperlink
                             (sort-by (fn [x] (- (.getThreadCpuTime (ManagementFactory/getThreadMXBean) (.getThreadId x)))))
                             vec)
         ::getAllThreadIds #(->> (.getAllThreadIds x) (mapv (partial hash-map ::thread-id)))
         ::deadlocked-threads (vec (.findDeadlockedThreads x))
         ::isThreadCpuTimeSupported (.isThreadCpuTimeSupported x)
         ::setThreadCpuTimeEnabled #(.setThreadCpuTimeEnabled x true)}
      (with-meta
        {`clojure.core.protocols/nav
         (fn [o k v]
           (case k
             ::dumpAllThreads (if v (v))
             ::getAllThreadIds (if v (v))
             v))})))

  java.lang.management.ThreadInfo
  (datafy [^java.lang.management.ThreadInfo x]
    (-> {::name (.getThreadName x)
         ::state (str (.getThreadState x))
         ::thread-id (.getThreadId x)
         ::stack-trace #(vec (.getStackTrace x))
         ::blocked-time (.getBlockedTime x)
         ::waited-time (.getWaitedTime x)
         ;::toString (.toString x) ; visualvm dump
         ::lock-info (.getLockInfo x)
         ::cpu-time (.getThreadCpuTime (ManagementFactory/getThreadMXBean) (.getThreadId x))}
      (with-meta
        {`clojure.core.protocols/nav
         (fn [o k v]
           (case k
             ::stack-trace (if v (v))
             ;::toString (if v (v))
             v))})))

  java.lang.management.ThreadInfo/1 (datafy [x] (vec x))
  java.lang.StackTraceElement (datafy [x] {::toString (.toString x)}))