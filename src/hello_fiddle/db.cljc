(ns hello-fiddle.db
  (:require [contrib.str]
            [contrib.debug]
            [datascript.core :as d]
            [hello-fiddle.electronics :as el]
            [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]
            [missionary.core :as m])
  (:import  [hyperfiddle.electric Pending])
  #?(:cljs (:require-macros hello-fiddle.db)))

(e/def conn)
(e/def !db)
(e/def db)
(e/def parent-db)
(e/def !stage nil)
(e/def stage)

#?(:clj (defn transactT [conn tx-data] (m/sp (d/transact! conn tx-data) [::accepted])))

#?(:clj (defn transactAllT [conn tx-data*]
          (m/sp (loop [tx-data* tx-data*]
                  (when-some [[tx-data & more] tx-data*]
                    (prn :transacting tx-data)
                    (m/? (transactT conn tx-data))
                    (recur more))))))

#?(:clj (defn db-withT [db tx-data] (m/sp (prn :withing tx-data) [(:db-after (m/? (m/via m/blk (d/with db tx-data)))) [::accepted]])))

#?(:clj (defn rebaseAllT [!db tx-data*]
          (m/sp (loop [tx-data* tx-data*]
                  (when-some [[tx-data & more] tx-data*]
                    (prn :rebasing tx-data)
                    (let [[db] (m/? (db-withT @!db tx-data))]
                      (reset! !db db)
                      (recur more)))))))

(e/defn Commit [tx-data* off!] (off! (e/server (prn 'Commit) (new (e/task->cp (transactAllT conn tx-data*))))))

(e/defn StageCommitter [!parent-stage]
  (e/fn [tx-data* off!]
    (prn :StageCommit)
    (off! (contrib.debug/dbg :parent-stage (swap! !parent-stage merge tx-data*)))))

(e/defn Rebase [!db tx-data*] (e/server (prn 'Rebase) (new (e/task->cp (rebaseAllT !db tx-data*)))))

(e/defn Stage [!db tx-data]
  (e/server
    (prn 'Stage)
    (let [[db status] (new (e/task->cp (db-withT (e/snapshot db) tx-data)))]
      (reset! !db db)
      status)))

(defn ->->id [] (let [!i (long-array [0])] (fn [] (aset !i 0 (unchecked-inc (aget !i 0))))))

(e/defn RebaseThenStage [txs]
  ;; TODO e/for-by identity [_ [parent-db]]        ; rerun body on change of `parent-db`
  (case (e/server (new Rebase !db (e/snapshot (e/client stage)))) ; v2 coloring
    (let [->id (->->id)
          [{::keys [tx-data off!]} next!] (new el/DataHotSwitch (first txs))]
      (when next!
        (case (off! (e/server (new Stage !db tx-data)))
          (let [id (->id), v {::tx-data tx-data, ::off! (fn [& _] (swap! !stage dissoc id))}]
            (next! (swap! !stage assoc id v)))
          #_(next! (swap! !stage conj {::tx-data tx-data})))))))

(defmacro branch [App]                  ; v2 coloring
  `(e/server
     (binding [!db (atom db), parent-db db]
       (binding [db (e/watch !db)]
         (e/client
           (binding [Commit (if (contrib.debug/dbg !stage) (new StageCommitter !stage) Commit)
                     !stage (atom (sorted-map))]
             (binding [stage (vals (contrib.debug/dbg (e/watch !stage)))]
               (new RebaseThenStage (new ~App))
               nil)))))))

(e/defn StageUI [txs]
  (e/client
    (let [!busy (atom false), busy (e/watch !busy)
          button-stage (dom/button
                         (dom/text "commit")
                         (let [off! (new el/Switch (new el/On "click"))
                               disabled? (boolean (or busy (empty? stage)))]
                           (reset! !busy off!)
                           (dom/props {:disabled disabled?, :aria-busy disabled?})
                           (when (and off! (empty? txs))
                             (let [stage (contrib.debug/dbg (mapv ::tx-data (vals (first (reset-vals! !stage (sorted-map))))))]
                               (try (new Commit stage off!) (catch Pending _))
                               stage))))]
      (dom/pre (dom/props {:style {:align-self :start}}) (dom/text (contrib.str/pprint-str (mapv ::tx-data txs))))
      (dom/button
        (dom/text "discard")
        (let [off! (new el/Switch (new el/On "click"))
              disabled? (boolean (or busy (empty? stage)))]
          (reset! !busy off!)
          (dom/props {:disabled disabled?, :aria-busy disabled?})
          (when (and off! (empty? txs))
            (off! (case (e/server (reset! !db parent-db) nil)
                    (reset! !stage (sorted-map)))))))
      (dom/pre (dom/props {:style {:align-self :start}}) (dom/text (contrib.str/pprint-str (or button-stage stage)))))))
