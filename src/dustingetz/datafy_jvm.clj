(ns dustingetz.datafy-jvm
  (:import java.lang.management.ThreadInfo
           com.sun.management.ThreadMXBean)
  (:require clojure.core.protocols))

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
     ::stack-trace (.getStackTrace x)
     ::blocked-time (.getBlockedTime x)
     ::waited-time (.getWaitedTime x)
     ;::toString (.toString x) ; visualvm dump
     ::lock-info (.getLockInfo x)})

  #_#_java.lang.management.ThreadInfo/1 (datafy [x] {})
  #_#_java.lang.StackTraceElement (datafy [x] {}))