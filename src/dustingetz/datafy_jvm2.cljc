(ns dustingetz.datafy-jvm2
  (:import [java.lang.management ManagementFactory]
           java.lang.management.ThreadInfo
           com.sun.management.ThreadMXBean)
  (:require [clojure.core.protocols :refer [Datafiable]]
            [dustingetz.identify :refer [Identifiable]]))

(defn resolve-thread [id] (apply com.sun.management.ThreadMXBean/.getThreadInfo
                            (ManagementFactory/getThreadMXBean) [id]))

(defn resolve-thread-manager [] (ManagementFactory/getThreadMXBean))

(extend-type com.sun.management.ThreadMXBean
  Identifiable (-identify [^com.sun.management.ThreadMXBean x] (-> x .getObjectName str))
  Datafiable
  (datafy [^com.sun.management.ThreadMXBean x]
    (-> {::object-name (str (.getObjectName x))
         ::thread-count (.getThreadCount x)
         ::dumpAllThreads #(->> (.dumpAllThreads x true true) ; hyperlink
                             (sort-by (fn [x] (- (.getThreadCpuTime (ManagementFactory/getThreadMXBean) (.getThreadId x)))))
                             vec)
         ::getAllThreadIds #(->> (.getAllThreadIds x) (mapv (partial hash-map ::thread-id)))
         ::deadlocked-threads (vec (.findDeadlockedThreads x))
         #_#_::isThreadCpuTimeSupported (.isThreadCpuTimeSupported x)
         #_#_::setThreadCpuTimeEnabled #(.setThreadCpuTimeEnabled x true)}
      (with-meta
        {`clojure.core.protocols/nav
         (fn [o k v]
           (case k
             ::dumpAllThreads (if v (v))
             ::getAllThreadIds (if v (v))
             #_#_::setThreadCpuTimeEnabled (if v (v))
             v))}))))

(extend-type java.lang.management.ThreadInfo
  Identifiable (-identify [^java.lang.management.ThreadInfo x] (.getThreadName x))
  Datafiable
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
             v))}))))

(extend-type java.lang.management.ThreadInfo/1
  Identifiable (-identify [x] (assert false (str "-identify unimplemented, x: " x)))
  Datafiable (datafy [x] (vec x)))

(extend-type java.lang.StackTraceElement
  Identifiable (-identify [^java.lang.StackTraceElement x] (hash x)) ; better to use local index fallback
  Datafiable (datafy [^java.lang.StackTraceElement x] {::toString (.toString x)})) ; members are private
