(ns dustingetz.nav-jvm-deep
  "Deep JVM diagnostics browser: threads, memory pools, GC, class loading,
   deadlock detection, thread CPU time, stack traces."
  (:require
   [dustingetz.nav-util.shell :as shell]
   [hyperfiddle.hfql2 :as hfql :refer [hfql]]
   [hyperfiddle.hfql2.protocols :refer [Identifiable Suggestable -hfql-resolve]])
  (:import
   [java.lang.management ManagementFactory ThreadMXBean MemoryMXBean
    MemoryPoolMXBean GarbageCollectorMXBean RuntimeMXBean
    ClassLoadingMXBean CompilationMXBean ThreadInfo MemoryUsage
    OperatingSystemMXBean]))

;; -- Records --

(defrecord JvmThread      [id name state cpu-time-ms user-time-ms blocked-count blocked-time
                            waited-count waited-time lock-name lock-owner-name daemon suspended
                            stack-depth])
(defrecord JvmMemoryPool  [name type usage-used usage-max usage-committed peak-used peak-max
                            collection-usage-used])
(defrecord JvmGc          [name collection-count collection-time-ms memory-pool-names])
(defrecord JvmClassInfo   [loaded-count total-loaded-count unloaded-count])
(defrecord JvmCompilation [name total-compilation-time-ms supports-monitoring])
(defrecord JvmRuntime     [name spec-version vm-name vm-vendor vm-version uptime-ms
                            start-time input-arguments boot-class-path class-path])
(defrecord JvmOsInfo      [name version arch available-processors])
(defrecord JvmDeadlock    [thread-id thread-name lock-name lock-owner-name])
(defrecord JvmHeapSummary [heap-used heap-max heap-committed nonheap-used nonheap-max
                            nonheap-committed pending-finalization])
(defrecord ThreadRef      [id])

;; -- MXBean accessors --

(defn- tmx ^ThreadMXBean [] (ManagementFactory/getThreadMXBean))
(defn- mmx ^MemoryMXBean [] (ManagementFactory/getMemoryMXBean))
(defn- rmx ^RuntimeMXBean [] (ManagementFactory/getRuntimeMXBean))
(defn- omx ^OperatingSystemMXBean [] (ManagementFactory/getOperatingSystemMXBean))
(defn- cmx ^CompilationMXBean [] (ManagementFactory/getCompilationMXBean))
(defn- clmx ^ClassLoadingMXBean [] (ManagementFactory/getClassLoadingMXBean))

;; -- Helpers --

(defn- usage-map [^MemoryUsage u]
  (when u {:used (.getUsed u) :max (.getMax u) :committed (.getCommitted u)}))

(defn- nanos->ms [n] (when n (quot n 1000000)))

;; -- Data functions --

(defn all-threads []
  (let [mx (tmx)
        ids (.getAllThreadIds mx)
        infos (.getThreadInfo mx ids 0)]
    (->> infos
      (remove nil?)
      (mapv (fn [^ThreadInfo ti]
              (let [tid (.getThreadId ti)]
                (->JvmThread
                  tid
                  (.getThreadName ti)
                  (str (.getThreadState ti))
                  (nanos->ms (try (.getThreadCpuTime mx tid) (catch Exception _ -1)))
                  (nanos->ms (try (.getThreadUserTime mx tid) (catch Exception _ -1)))
                  (.getBlockedCount ti)
                  (.getBlockedTime ti)
                  (.getWaitedCount ti)
                  (.getWaitedTime ti)
                  (some-> (.getLockInfo ti) str)
                  (.getLockOwnerName ti)
                  (.isSuspended ti)
                  (.isSuspended ti)
                  (count (.getStackTrace ti)))))))))

(defn find-thread [id]
  (let [mx (tmx)
        ^ThreadInfo ti (.getThreadInfo mx (long id) 100)] ; 100 stack frames
    (when ti
      (->JvmThread
        (.getThreadId ti)
        (.getThreadName ti)
        (str (.getThreadState ti))
        (nanos->ms (try (.getThreadCpuTime mx (long id)) (catch Exception _ -1)))
        (nanos->ms (try (.getThreadUserTime mx (long id)) (catch Exception _ -1)))
        (.getBlockedCount ti)
        (.getBlockedTime ti)
        (.getWaitedCount ti)
        (.getWaitedTime ti)
        (some-> (.getLockInfo ti) str)
        (.getLockOwnerName ti)
        (.isSuspended ti)
        (.isSuspended ti)
        (count (.getStackTrace ti))))))

(defn memory-pools []
  (mapv (fn [^MemoryPoolMXBean p]
          (let [u (usage-map (.getUsage p))
                pk (usage-map (.getPeakUsage p))
                cu (try (usage-map (.getCollectionUsage p)) (catch Exception _ nil))]
            (->JvmMemoryPool
              (.getName p)
              (str (.getType p))
              (shell/format-bytes (:used u))
              (shell/format-bytes (:max u))
              (shell/format-bytes (:committed u))
              (shell/format-bytes (:used pk))
              (shell/format-bytes (:max pk))
              (shell/format-bytes (:used cu)))))
    (ManagementFactory/getMemoryPoolMXBeans)))

(defn gc-collectors []
  (mapv (fn [^GarbageCollectorMXBean g]
          (->JvmGc
            (.getName g)
            (.getCollectionCount g)
            (.getCollectionTime g)
            (vec (.getMemoryPoolNames g))))
    (ManagementFactory/getGarbageCollectorMXBeans)))

(defn class-loading []
  (let [mx (clmx)]
    (->JvmClassInfo
      (.getLoadedClassCount mx)
      (.getTotalLoadedClassCount mx)
      (.getUnloadedClassCount mx))))

(defn compilation-info []
  (when-let [mx (cmx)]
    (->JvmCompilation
      (.getName mx)
      (try (.getTotalCompilationTime mx) (catch Exception _ -1))
      (.isCompilationTimeMonitoringSupported mx))))

(defn runtime-info []
  (let [mx (rmx)]
    (->JvmRuntime
      (.getName mx)
      (.getSpecVersion mx)
      (.getVmName mx)
      (.getVmVendor mx)
      (.getVmVersion mx)
      (.getUptime mx)
      (.getStartTime mx)
      (vec (.getInputArguments mx))
      (try (.getBootClassPath mx) (catch Exception _ "n/a"))
      (.getClassPath mx))))

(defn os-info []
  (let [mx (omx)]
    (->JvmOsInfo
      (.getName mx)
      (.getVersion mx)
      (.getArch mx)
      (.getAvailableProcessors mx))))

(defn deadlocked-threads []
  (let [mx (tmx)
        ids (or (.findDeadlockedThreads mx) (long-array 0))]
    (if (pos? (alength ids))
      (mapv (fn [^ThreadInfo ti]
              (->JvmDeadlock
                (.getThreadId ti)
                (.getThreadName ti)
                (some-> (.getLockInfo ti) str)
                (.getLockOwnerName ti)))
        (.getThreadInfo mx ids))
      [])))

(defn heap-summary []
  (let [mx (mmx)
        h (usage-map (.getHeapMemoryUsage mx))
        nh (usage-map (.getNonHeapMemoryUsage mx))]
    (->JvmHeapSummary
      (shell/format-bytes (:used h))
      (shell/format-bytes (:max h))
      (shell/format-bytes (:committed h))
      (shell/format-bytes (:used nh))
      (shell/format-bytes (:max nh))
      (shell/format-bytes (:committed nh))
      (.getObjectPendingFinalizationCount mx))))

;; -- Named accessors --

(defn thread-name [t] (:name t))
(defn thread-id [t] (->ThreadRef (:id t)))
(defn deadlock-thread-name [d] (:thread-name d))
(defn pool-name [p] (:name p))
(defn gc-name [g] (:name g))

;; -- Protocol extensions --

(extend-type ThreadRef
  Identifiable (-identify [r] `(find-thread ~(:id r)))
  Suggestable (-suggest [_] (hfql [:id])))

(extend-type JvmThread
  Identifiable (-identify [t] `(find-thread ~(:id t)))
  Suggestable (-suggest [_] (hfql [:id :name :state :cpu-time-ms :user-time-ms
                                    :blocked-count :waited-count :lock-name
                                    :lock-owner-name :stack-depth])))

(extend-type JvmMemoryPool
  Identifiable (-identify [p] `(find-memory-pool ~(:name p)))
  Suggestable (-suggest [_] (hfql [:name :type :usage-used :usage-max :usage-committed
                                    :peak-used :peak-max :collection-usage-used])))

(extend-type JvmGc
  Identifiable (-identify [g] `(find-gc ~(:name g)))
  Suggestable (-suggest [_] (hfql [:name :collection-count :collection-time-ms :memory-pool-names])))

(extend-type JvmClassInfo
  Identifiable (-identify [_] `(class-loading))
  Suggestable (-suggest [_] (hfql [:loaded-count :total-loaded-count :unloaded-count])))

(extend-type JvmCompilation
  Identifiable (-identify [_] `(compilation-info))
  Suggestable (-suggest [_] (hfql [:name :total-compilation-time-ms :supports-monitoring])))

(extend-type JvmRuntime
  Identifiable (-identify [_] `(runtime-info))
  Suggestable (-suggest [_] (hfql [:name :spec-version :vm-name :vm-vendor :vm-version
                                    :uptime-ms :start-time])))

(extend-type JvmOsInfo
  Identifiable (-identify [_] `(os-info))
  Suggestable (-suggest [_] (hfql [:name :version :arch :available-processors])))

(extend-type JvmDeadlock
  Identifiable (-identify [d] `(find-thread ~(:thread-id d)))
  Suggestable (-suggest [_] (hfql [:thread-id :thread-name :lock-name :lock-owner-name])))

(extend-type JvmHeapSummary
  Identifiable (-identify [_] `(heap-summary))
  Suggestable (-suggest [_] (hfql [:heap-used :heap-max :heap-committed
                                    :nonheap-used :nonheap-max :nonheap-committed
                                    :pending-finalization])))

;; -- Sitemap --

(def sitemap
  {`all-threads
   (hfql {(all-threads) {* ^{::hfql/select `find-thread}
                           [^{::hfql/link `(find-thread ~'%)} thread-name
                            :state :cpu-time-ms :blocked-count :waited-count]}})

   `find-thread
   (hfql [:id :name :state :cpu-time-ms :user-time-ms
          :blocked-count :blocked-time :waited-count :waited-time
          :lock-name :lock-owner-name :daemon :suspended :stack-depth])

   `memory-pools
   (hfql {(memory-pools) {* [pool-name :type :usage-used :usage-max :peak-used]}})

   `gc-collectors
   (hfql {(gc-collectors) {* [gc-name :collection-count :collection-time-ms :memory-pool-names]}})

   `heap-summary
   (hfql {(heap-summary) [:heap-used :heap-max :heap-committed
                           :nonheap-used :nonheap-max :nonheap-committed
                           :pending-finalization]})

   `deadlocked-threads
   (hfql {(deadlocked-threads) {* ^{::hfql/select `find-thread}
                                  [^{::hfql/link `(find-thread ~'%)} deadlock-thread-name
                                   :lock-name :lock-owner-name]}})

   `runtime-info
   (hfql {(runtime-info) [:name :spec-version :vm-name :vm-vendor :vm-version :uptime-ms]})

   `os-info
   (hfql {(os-info) [:name :version :arch :available-processors]})

   `class-loading
   (hfql {(class-loading) [:loaded-count :total-loaded-count :unloaded-count]})

   `compilation-info
   (hfql {(compilation-info) [:name :total-compilation-time-ms :supports-monitoring]})})

;; -- Resolvers --

(defmethod -hfql-resolve `find-thread [[_ id]] (find-thread id))
(defmethod -hfql-resolve `all-threads [_] (all-threads))
(defmethod -hfql-resolve `memory-pools [_] (memory-pools))
(defmethod -hfql-resolve `gc-collectors [_] (gc-collectors))
(defmethod -hfql-resolve `heap-summary [_] (heap-summary))
(defmethod -hfql-resolve `deadlocked-threads [_] (deadlocked-threads))
(defmethod -hfql-resolve `runtime-info [_] (runtime-info))
(defmethod -hfql-resolve `os-info [_] (os-info))
(defmethod -hfql-resolve `class-loading [_] (class-loading))
(defmethod -hfql-resolve `compilation-info [_] (compilation-info))
(defmethod -hfql-resolve `find-memory-pool [[_ name]] ; for nav from gc pool names
  (first (filter #(= name (:name %)) (memory-pools))))
(defmethod -hfql-resolve `find-gc [[_ name]]
  (first (filter #(= name (:name %)) (gc-collectors))))
