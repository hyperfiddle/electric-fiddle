(ns dustingetz.nav-jvm
  #?(:clj (:import com.sun.management.ThreadMXBean
                   java.lang.management.ManagementFactory
                   java.lang.management.MemoryMXBean
                   java.lang.management.OperatingSystemMXBean
                   java.lang.management.RuntimeMXBean
                   java.lang.management.ThreadInfo))
  (:require [hyperfiddle.hfql0 #?(:clj :as :cljs :as-alias) hfql]))

#?(:clj (defn getPlatformManagementInterfaces [] (vec (ManagementFactory/getPlatformManagementInterfaces))))
#?(:clj (defn getPlatformMXBean [clazz] (ManagementFactory/getPlatformMXBean clazz)))
#?(:clj (defn getPlatformMXBean2 [class-id] (ManagementFactory/getPlatformMXBean (Class/forName class-id))))
#?(:clj (defn resolve-class [class-id] (Class/forName class-id)))

(comment
  (getPlatformManagementInterfaces)
  (type (first *1)) := Class
  (getPlatformMXBean "java.lang.management.ThreadMXBean")
  (getPlatformMXBean "com.sun.management.ThreadMXBean") ; identical

  ; this sitemap needs unquote, i.e. to resolve % as a route
  {getPlatformManagementInterfaces (hfql/props [] {::hfql/select (resolve-class %)})
   resolve-class [getPlatformMXBean]
   getPlatformMXBean2 [type]})

#?(:clj (defn getThreadMXBean [] (ManagementFactory/getThreadMXBean))) ; todo hfql syntax
#?(:clj (defn getMemoryMXBean [] (ManagementFactory/getMemoryMXBean)))
#?(:clj (defn getRuntimeMXBean [] (ManagementFactory/getRuntimeMXBean)))
#?(:clj (defn getOperatingSystemMXBean [] (ManagementFactory/getOperatingSystemMXBean)))

#?(:clj (defn getAllThreads [mx] (->> (.getAllThreadIds mx) (map #(.getThreadInfo mx %)))))
#?(:clj (defn resolve-thread [id] (-> (ManagementFactory/getThreadMXBean) (.getThreadInfo id))))

(comment
  (getAllThreads (getThreadMXBean))
  (resolve-thread 1)
  )

#?(:clj (def sitemap
          (hfql/sitemap
            {getThreadMXBean [getAllThreads]
             getMemoryMXBean []
             getRuntimeMXBean []
             getOperatingSystemMXBean []})))

#?(:clj (extend-protocol hfql/Identifiable
          com.sun.management.ThreadMXBean (-identify [x] (-> x .getObjectName str))
          java.lang.management.MemoryMXBean (-identify [x] (-> x .getObjectName str))
          java.lang.management.OperatingSystemMXBean (-identify [x] (-> x .getObjectName str))
          java.lang.management.RuntimeMXBean (-identify [x] (-> x .getObjectName str))
          java.lang.management.ThreadInfo (-identify [x] (.getThreadId x))))

#?(:clj (extend-protocol hfql/Suggestable
          com.sun.management.ThreadMXBean (-suggest [x] (hfql/pull-spec [type .findDeadlockedThreads]))
          java.lang.management.MemoryMXBean (-suggest [x] (hfql/pull-spec [type .getHeapMemoryUsage .getNonHeapMemoryUsage .getObjectPendingFinalizationCount]))
          java.lang.management.OperatingSystemMXBean (-suggest [x] (hfql/pull-spec [type .getArch .getAvailableProcessors .getCpuLoad .getSystemCpuLoad]))
          java.lang.management.RuntimeMXBean (-suggest [x] (hfql/pull-spec [type .getLibraryPath .getSystemProperties .getUptime .getPid]))
          java.lang.management.ThreadInfo (-suggest [x] (hfql/pull-spec [type .getThreadId .getLockInfo .getThreadName .getThreadState .getWaitedCount .getWaitedTime]))))
