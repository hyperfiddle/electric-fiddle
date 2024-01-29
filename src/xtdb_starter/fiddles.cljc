(ns xtdb-starter.fiddles
  (:require #?(:clj [clojure.java.io :as io])
            #?(:clj [xtdb.api :as xt])
            [hyperfiddle.electric-dom2 :as dom]
            [hyperfiddle.electric :as e]
            [xtdb-starter.todo-list :refer [Todo-list]]))

#?(:clj (defonce !xtdb-node (atom nil)))

#?(:clj
   (defn start-xtdb! [] ; from XTDB’s getting started: xtdb-in-a-box
     (assert (= "true" (System/getenv "XTDB_ENABLE_BYTEUTILS_SHA1"))) ; App must start with this env var set to "true"
     (letfn [(kv-store [dir] {:kv-store {:xtdb/module 'xtdb.rocksdb/->kv-store
                                         :db-dir (io/file dir)
                                         :sync? true}})]
       (or @!xtdb-node
         (reset! !xtdb-node
           (xt/start-node
             {:xtdb/tx-log (kv-store "data/dev/tx-log")
              :xtdb/document-store (kv-store "data/dev/doc-store")
              :xtdb/index-store (kv-store "data/dev/index-store")}))))))

(e/defn XTDB-Starter []
  (e/server
    (if-let [!xtdb (try (e/offload #(start-xtdb!))
                          (catch hyperfiddle.electric.Pending _
                            nil))]
      (Todo-list. !xtdb)
      (e/client
        (dom/p (dom/text "XTDB is starting ..."))))))

(e/def fiddles {`XTDB-Starter XTDB-Starter})

(e/defn FiddleMain [ring-request]
  (e/client
    (binding [dom/node js/document.body]
      (XTDB-Starter.))))

(comment
  (.close @!xtdb-node)
  (reset! !xtdb-node nil))
