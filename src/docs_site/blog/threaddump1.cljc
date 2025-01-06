(ns docs-site.blog.threaddump1
  #?(:clj (:import [java.lang.management ManagementFactory]))
  (:require clojure.string
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
(e/defn ThreadDump1 []
  (e/client
    (dom/div
      (dom/props {:class "ThreadDump"}) (dom/style (dom/text css))
      (EasyTable "Thread Dumper"
        (e/server (fn query [search]
                    (let [dump-str (get-thread-dump)]
                      (->> (clojure.string/split-lines dump-str)
                        (filter #(includes-str? % search))
                        vec))))
        (e/fn Row [x] (dom/td (dom/text x)))))))

(def css "
.ThreadDump > fieldset { position:fixed; top:0em; bottom:0; left:0; right:0; }
.ThreadDump table { grid-template-columns: 1fr; }")