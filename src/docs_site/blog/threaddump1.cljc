(ns docs-site.blog.threaddump1
  #?(:clj (:import [java.lang.management ManagementFactory]))
  (:require clojure.string
            [dustingetz.str :refer [includes-str?]]
            [hyperfiddle.electric-forms5 :refer [TablePicker! Input*]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]))

#?(:clj (defn get-thread-dump [] ; claude
          (let [thread-bean (ManagementFactory/getThreadMXBean)
                thread-infos (.dumpAllThreads thread-bean true true)]
            (with-out-str
              (doseq [thread-info thread-infos]
                (print (.toString thread-info)))))))

#?(:clj
   (defn query [search]
     (let [dump-str (get-thread-dump)]
       (->> (clojure.string/split-lines dump-str)
         (filter #(includes-str? % search))
         vec))))

(declare css)

(e/defn ThreadDump1 []
  (e/client
    (dom/div (dom/props {:class "ThreadDump"}) (dom/style (dom/text css))
      (dom/fieldset
        (let [xs! (dom/legend
                    (dom/text "Thread Dumper" " ")
                    (let [search (Input* "")
                          xs! (e/server (e/Offload #(query search)))]
                      (dom/text (str " (" (e/server (count xs!)) " items) "))
                      xs!))]
          (TablePicker! ::_ nil (e/server (count xs!))
            (e/fn [i]
              (e/server (when-some [x (nth xs! i nil)]
                          (e/client (dom/td (dom/text x)))
                          )))))))))

(def css (str hyperfiddle.electric-forms5/css "
.ThreadDump > fieldset { position:fixed; top:0em; bottom:0; left:0; right:0; }
.ThreadDump table { grid-template-columns: 1fr; }"))