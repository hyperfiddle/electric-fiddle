(ns dustingetz.datafy-jvm
  #?(:clj (:import [java.lang.management ManagementFactory]))
  (:require clojure.string
            [contrib.str :refer [includes-str?]]))

#?(:clj
   (extend-protocol clojure.core.protocols/Datafiable
     com.sun.management.ThreadMXBean
     (datafy [x]
       {::dump-all-threads #(.dumpAllThreads x true true) ; hyperlink
        ::object-name (str (.getObjectName x))})

     ;java.lang.management.ThreadInfo/1 (datafy [x] {})

     java.lang.management.ThreadInfo
     (datafy [^java.lang.management.ThreadInfo x]
       {::name (.getThreadName x)
        ::id (.getThreadId x)
        ::state (str (.getThreadState x))
        ::stack-trace (.getStackTrace x)
        ::blocked-time (.getBlockedTime x)
        ::waited-time (.getWaitedTime x)
        ::toString (.toString x)
        ::lock-info (.getLockInfo x)})

     #_#_java.lang.StackTraceElement (datafy [x] {})))

(comment
  (require '[clojure.datafy :refer [datafy]])
  (def x (ManagementFactory/getThreadMXBean))
  (tap> x)
  (type x) := com.sun.management.internal.HotSpotThreadImpl
  (tap> com.sun.management.internal.HotSpotThreadImpl)
  ; extends
  (tap> com.sun.management.ThreadMXBean)
  (tap> sun.management.ThreadImpl)
  ; extends
  (tap> java.lang.management.ThreadMXBean)

  (def x (ManagementFactory/getThreadMXBean))
  (type x)
  (def m (datafy x))
  (::object-name m)
  (fn? (::dump-all-threads m))
  (def xs ((::dump-all-threads m)))
  (count xs)
  (def t (first xs))
  (tap> t)
  (str t)
  (def trace (.getStackTrace t))
  (tap> trace)
  (.toString trace)


  (def y (.dumpAllThreads x true true))
  (type y)
  (tap> y)
  (def q (first y))
  (type q) := java.lang.management.ThreadInfo
  (tap> q)
  (tap> java.lang.management.ThreadInfo)

  (str q)
  (get-thread-dump)

  (datafy q)

  ; "main" prio=5 Id=1 TIMED_WAITING at java.base@17.0.3/java.lang.Thread.sleep(Native Method) at nrepl.cmdline$dispatch_commands.invokeStatic(cmdline.clj:452) at nrepl.cmdline$dispatch_commands.invoke(cmdline.clj:436) at nrepl.cmdline$_main.invokeStatic(cmdline.clj:459) at nrepl.cmdline$_main.doInvoke(cmdline.clj:454) at app//clojure.lang.RestFn.invoke(RestFn.java:424) at user$eval70040.invokeStatic(form-init9294830033559010989.clj:1) at user$eval70040.invoke(form-init9294830033559010989.clj:1) ...

  )

