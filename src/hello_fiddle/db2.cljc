(ns hello-fiddle.db2
  (:require
   [contrib.str]
   [contrib.debug]
   [datascript.core :as d]
   [hello-fiddle.electronics :as el]
   [hyperfiddle.electric :as e]
   [hyperfiddle.electric-dom2 :as dom]
   [missionary.core :as m]))

#?(:clj (defn transactT [conn tx-data] (m/sp (prn :transacting tx-data) (d/transact! conn tx-data) [::accepted])))
#?(:clj (defn db-withT [db tx-data] (m/sp (prn :withing tx-data) [(:db-after (m/? (m/via m/blk (d/with db tx-data)))) [::accepted]])))
#?(:clj (defn rebaseAllT [!db tx-data*]
          (m/sp (loop [tx-data* tx-data*]
                  (when-some [[tx-data & more] tx-data*]
                    (prn :rebasing tx-data)
                    (let [[db] (m/? (db-withT @!db tx-data))]
                      (reset! !db db)
                      (recur more)))))))

(e/def conn)
(e/def !db)
(e/def db)
(e/def db-latch!)
(e/def !parent-db)
(e/def parent-db)
(e/def !parent-stage)
(e/def !stage nil)
(e/def stage)

;; app tx data is
(comment {1 {::tx-data ['..], ::off! (fn [_])}
          2 {::tx-data ['..], ::off! (fn [_])}})

(defn ->->id [] (let [!i (long-array [0])] (fn [] (aset !i 0 (unchecked-inc (aget !i 0))))))
(defonce ->tx-id (->->id))

(defn tx-conj [txm tx] (cond-> txm tx (assoc (->tx-id) tx)))

(e/defn Commit [{::keys [tx-data off!]}]
  (off! (e/server (prn 'Commit) (new (e/task->cp (transactT conn tx-data))))))

(let [->off (fn [!stage tx-id] (fn f ([] (f nil)) ([v] (swap! !stage dissoc tx-id) v)))]
  (defn add-tx-data-to-stage [tx-data !stage]
    (let [tx-id (->tx-id), stagev {::tx-data tx-data, ::off! (->off !stage tx-id)}]
      (swap! !stage assoc tx-id stagev))))

(defmacro stage-tx [!db !stage tx]
  `(let [{tx-data# ::tx-data, off# ::off!} ~tx
         status# (e/server
                   (let [[db# status#] (new (e/task->cp (db-withT @~!db tx-data#)))]
                     (reset! ~!db db#)
                     status#))]
     (case status#
       (do (add-tx-data-to-stage tx-data# ~!stage)
           (off# status#)))))

(e/defn StageCommitter [tx]
  (prn 'StageCommit)
  (stage-tx !parent-db !parent-stage tx))

(e/defn Rebase [!db tx-data*] (e/server (prn 'Rebase) (new (e/task->cp (rebaseAllT !db tx-data*)))))

(e/defn RebaseThenStage [txs]
  ;; TODO rerun body on change of `parent-db`
  (case (e/server
          (e/for-by identity [_ [parent-db]]
            (new Rebase !db (e/snapshot (e/client (mapv ::tx-data (vals stage)))))))
    (let [[tx next!] (new el/DataHotSwitch (first (vals txs)))]
      (when next! (next! (stage-tx !db !stage tx))))))

(defmacro branch [App]
  `(e/server
     (binding [!parent-db !db, parent-db db, !db (atom nil)]
       (reset! !db parent-db)
       (let [[db# db-latch!#] (new el/DataLatch (e/watch !db))]
         (binding [db db#, db-latch! db-latch!#]
           (e/client
             (binding [Commit (if !stage StageCommitter Commit)
                       !parent-stage !stage
                       !stage (atom (sorted-map))]
               (binding [stage (e/watch !stage)]
                 (new RebaseThenStage (new ~App))
                 nil))))))))

(e/defn StageUI [txs]
  (e/client
    (let [!busy (atom false), busy (e/watch !busy)]
      (dom/pre (dom/props {:style {:align-self "start"}}) (dom/text (contrib.str/pprint-str (mapv ::tx-data (vals txs)))))
      (dom/button
        (dom/text "commit")
        (let [click-off! (new el/Switch (new el/On "click"))
              disabled? (boolean (or busy (empty? stage)))]
          (reset! !busy click-off!)
          (dom/props {:disabled disabled?, :aria-busy disabled?})
          (when (and click-off! (empty? txs))
            (e/server
              (let [db-unlatch! (db-latch!)]
                (e/client
                  (if (seq stage)
                    (let [[tx next!] (new el/DataHotSwitch (first (vals stage)))]
                      (when next! (next! (new Commit tx))))
                    (click-off! (e/server (db-unlatch!))))))))))
      (dom/button
        (dom/text "discard")
        (let [off! (new el/Switch (new el/On "click"))
              disabled? (boolean (or busy (empty? stage)))]
          (reset! !busy off!)
          (dom/props {:disabled disabled?, :aria-busy disabled?})
          (when (and off! (empty? txs))
            (off! (case (e/server (reset! !db parent-db) nil)
                    (reset! !stage (sorted-map)))))))
      (dom/pre (dom/props {:style {:align-self "start"}}) (dom/text (contrib.str/pprint-str (mapv ::tx-data (vals stage))))))))
