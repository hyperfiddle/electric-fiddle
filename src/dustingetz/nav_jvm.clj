(ns dustingetz.nav-jvm
  (:require
   [hyperfiddle.hfql2 :as hfql :refer [hfql]]
   [hyperfiddle.hfql2.protocols :as hfqlp])
  (:import
   com.sun.management.ThreadMXBean
   java.lang.management.ManagementFactory
   java.lang.management.MemoryMXBean
   java.lang.management.OperatingSystemMXBean
   java.lang.management.RuntimeMXBean
   java.lang.management.ThreadInfo))

(defn getPlatformManagementInterfaces [] (vec (ManagementFactory/getPlatformManagementInterfaces)))
(defn getPlatformMXBean [clazz] (ManagementFactory/getPlatformMXBean clazz))
(defn getPlatformMXBean2 [class-id] (ManagementFactory/getPlatformMXBean (Class/forName class-id)))
(defn resolve-class [class-id] (Class/forName class-id))

(comment
  (getPlatformManagementInterfaces)
  (type (first *1)) := Class
  (getPlatformMXBean "java.lang.management.ThreadMXBean")
  (getPlatformMXBean "com.sun.management.ThreadMXBean") ; identical

  ;; this sitemap needs unquote, i.e. to resolve % as a route
  ;; TODO: Broken in hfql2?
  ;; {getPlatformManagementInterfaces (hfql/props [] {::hfql/select (resolve-class %)})
  ;;  resolve-class [getPlatformMXBean]
  ;;  getPlatformMXBean2 [type]}
  :-)

(defn getThreadMXBean [] (ManagementFactory/getThreadMXBean)) ; todo hfql syntax
(defn getMemoryMXBean [] (ManagementFactory/getMemoryMXBean))
(defn getRuntimeMXBean [] (ManagementFactory/getRuntimeMXBean))
(defn getOperatingSystemMXBean [] (ManagementFactory/getOperatingSystemMXBean))

(defn getAllThreads [mx] (->> (.getAllThreadIds mx) (map #(.getThreadInfo mx %))))
(defn resolve-thread [id] (-> (ManagementFactory/getThreadMXBean) (.getThreadInfo id)))

(comment
  (getAllThreads (getThreadMXBean))
  (resolve-thread 1))

(extend-type com.sun.management.ThreadMXBean
  hfqlp/Identifiable (-identify [x] (-> x .getObjectName str))
  hfqlp/Suggestable (-suggest [o] (hfql [type .findDeadlockedThreads])))

(extend-type java.lang.management.MemoryMXBean
  hfqlp/Identifiable (-identify [x] (-> x .getObjectName str))
  hfqlp/Suggestable (-suggest [o] (hfql [type .getHeapMemoryUsage .getNonHeapMemoryUsage .getObjectPendingFinalizationCount])))

(extend-type java.lang.management.OperatingSystemMXBean
  hfqlp/Identifiable (-identify [x] (-> x .getObjectName str))
  hfqlp/Suggestable (-suggest [o] (hfql [type .getArch .getAvailableProcessors .getCpuLoad .getSystemCpuLoad])))

(extend-type java.lang.management.ThreadInfo
  hfqlp/Identifiable (-identify [x] (.getThreadId x))
  hfqlp/Suggestable (-suggest [o] (hfql [type .getThreadId .getLockInfo .getThreadName .getThreadState .getWaitedCount .getWaitedTime])))

(extend-type java.lang.management.MemoryMXBean
  hfqlp/Identifiable (-identify [x] (-> x .getObjectName str))
  hfqlp/Suggestable (-suggest [x] (hfql [type])))
