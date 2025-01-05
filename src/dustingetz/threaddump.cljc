(ns dustingetz.threaddump
  #?(:clj (:import [java.lang.management ManagementFactory]))
  (:require clojure.core.protocols
            clojure.string
            [contrib.str :refer [includes-str?]]
            [dustingetz.easy-table :refer [EasyTable]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]))

#?(:clj (defn get-thread-dump []
          (let [thread-bean (ManagementFactory/getThreadMXBean)
                thread-infos (.dumpAllThreads thread-bean true true)]
            (apply str
              (for [thread-info thread-infos]
                (.toString thread-info))))))

(declare css)
(e/defn ThreadDump []
  (e/client (dom/props {:class "ThreadDump"}) (dom/style (dom/text css))
    (EasyTable "Thread Dumper"
      (e/server (fn query [search]
                  (let [dump-str (get-thread-dump)]
                    (->> (clojure.string/split-lines dump-str)
                      (filter #(includes-str? % search))
                      vec))))
      (e/fn Row [x] (dom/td (dom/text (or x ".")))))))

(def css "
.ThreadDump fieldset { position:fixed; top:0em; bottom:0; left:0; right:0; }
.ThreadDump table { grid-template-columns: auto; }
")

(comment
  (let [dump-str (get-thread-dump)]
    (->> (clojure.string/split-lines dump-str)
      #_(filter #(includes-str? % search))
      vec))
  )

(comment
  (require '[clojure.datafy :refer [datafy]])
  (def x (ManagementFactory/getThreadMXBean))
  (datafy (type x))
  (type x)
  (def y (.dumpAllThreads x true true))
  (type y)
  (def q (first y))
  (type q)
  (str q)
  (get-thread-dump)

  (datafy q)

  )

#?(:clj
   (extend-protocol clojure.core.protocols/Datafiable
     com.sun.management.internal.HotSpotThreadImpl
     (datafy [x] {})

     ;java.lang.management.ThreadInfo/1 (datafy [x] {})

     java.lang.management.ThreadInfo
     (datafy [x]
       {:name (.getThreadName x)
        :id (.getThreadId x)
        :state (.getThreadState x)
        :stack-trace (.getStackTrace x)
        :blocked-time (.getBlockedTime x)
        :waited-time (.getWaitedTime x)})
     ))