(ns dustingetz.datafy-jvm
  (:import [java.lang.management ManagementFactory]
           java.lang.management.ThreadInfo
           com.sun.management.ThreadMXBean)
  (:require clojure.core.protocols))

(defn resolve-thread [id]
  (apply com.sun.management.ThreadMXBean/.getThreadInfo
    (ManagementFactory/getThreadMXBean) [id]))

(defn resolve-thread-manager []
  (ManagementFactory/getThreadMXBean))

(comment (resolve-thread 1))

(extend-protocol clojure.core.protocols/Datafiable
  com.sun.management.ThreadMXBean
  (datafy [x]
    {::object-name (str (.getObjectName x))
     ::thread-count (.getThreadCount x)
     ::dumpAllThreads #(.dumpAllThreads x true true) ; hyperlink
     ::getAllThreadIds #(->> (.getAllThreadIds x) (mapv (partial hash-map ::thread-id)))
     ::deadlocked-threads (vec (.findDeadlockedThreads x))})

  java.lang.management.ThreadInfo
  (datafy [^java.lang.management.ThreadInfo x]
    {::name (.getThreadName x)
     ::thread-id (.getThreadId x)
     ::state (str (.getThreadState x))
     ::stack-trace #(vec (.getStackTrace x))
     ::blocked-time (.getBlockedTime x)
     ::waited-time (.getWaitedTime x)
     ;::toString (.toString x) ; visualvm dump
     ::lock-info (.getLockInfo x)})

  java.lang.management.ThreadInfo/1 (datafy [x] (vec x))
  java.lang.StackTraceElement (datafy [x] {::toString (.toString x)}))