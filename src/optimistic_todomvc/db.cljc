(ns optimistic-todomvc.db
  (:require [hyperfiddle.electric-de :as e :refer [$]]
            [hyperfiddle.electric.impl.lang-de2 :as lang]
            [missionary.core :as m]
            [hyperfiddle.incseq :as i]))

(declare conn !db db latch-db !pdb pdb !!stage stage !!pstage Committer %persist %with e a v !status status)
(def ->->id (partial swap! (atom 0) inc))
(defonce ->tx-id (->->id))

(defn %rebaseAll [!db tx*]
  (m/sp (loop [db @!db]
          (let [ndb (loop [ndb db, tx* tx*]
                      (if-some [[tx tx*] tx*]
                        (let [[t v] (m/? (%with db tx))] (if (= ::accepted t)  (recur v tx*)  (throw v)))
                        ndb))]
            (if (compare-and-set! !db db ndb)  [::accepted ndb]  (recur @!db))))))

(let [->token (fn [!!stage tx-id] (fn f ([] (f nil)) ([v] (!!stage tx-id {} nil) v)))]
  (defn add-tx-data-to-stage [tx-data !!stage]
    (let [tx-id (->tx-id), stagev {::tx tx-data, ::spend (->token !!stage tx-id)}]
      (!!stage tx-id {} stagev))))

(letfn [(handle-with [!db [t v]] (if (= ::accepted t) (do (reset! !db v) t) (throw v)))
        (stage [!!stage tx spend t] (add-tx-data-to-stage tx !!stage) (spend t))]
  (e/defn Stage [!db !!stage {::keys [tx spend]}]
    (stage !!stage tx spend (e/server (handle-with !db ($ e/Task (%with @!db tx)))))))

(e/defn StageCommitter [dd] ($ Stage !pdb !!pstage dd))
(e/defn PersistCommitter [{::keys [tx spend]}] (spend (e/server ($ e/Task (%persist conn tx)))))

(e/defn First [v] (let [v (e/as-vec v)] (case v [] (e/amb) (nth v 0))))

(letfn [(%rebase [!db tx* _pdb] (%rebaseAll !db tx*))]
  (e/defn RebaseThenStage [dd*]
    (let [rebase-done (e/server ($ e/Task (%rebase !db (e/as-vec (e/client (::tx stage))) pdb)))
          [dd spend] ($ e/CyclicToken ($ First dd*))]
      (when spend (spend ({} rebase-done ($ Stage !db !!stage dd)))))))

(e/defn Branch [App]
  (let [!ndb (e/server (atom nil)), [ndb latch] (e/server ($ e/Latchable (e/watch !ndb)))
        !!nstage (e/client (i/spine))] ; next stage
    (binding [!pdb !db, pdb db, ; parent db
              !db !ndb, db ndb, ; next db
              latch-db latch
              Committer (e/client (if !!stage StageCommitter PersistCommitter))
              !!pstage !!stage, ; parent stage
              !!stage !!nstage, stage (e/client (e/join !!stage))]
      ($ RebaseThenStage ($ App)))))

(e/defn Empty? [v] (-> v e/pure i/count e/join zero?))

;; This is an extremely interesting pattern. It looks like a reduction with N
;; reduced steps. Looks like a reactive reduction (if that's a thing). Instead
;; of moving a pointer to the next iterator slot, we consume tokens of a stack,
;; reactively. This looks like a rotated form of iteration, like a catamorphic coroutine.
(e/defn Commit [spend dd*] ; would be called in the UI - example below.68M
  (when (and spend ($ Empty? dd*))
    (let [release (e/server (latch-db))] ; second value of e/Latchable line 41.
      ;; We start by latching the db and we release the db latch after all
      ;; commits are done, so we atomically see db -> db. Isn't this `locking` ?
      (if ($ Empty? stage)
        (spend (e/server (release)))
        (let [[dd spend] ($ e/CyclicToken ($ First stage))] ; will pop first value on ACK and rerun with new first value until no value left.
          (when spend (spend ($ Committer dd))))))))

(e/defn Discard [spend dd*]
  (when (and spend ($ Empty? dd*))
    (spend (reset! !!stage ({} (e/server (reset! !db pdb) nil) (i/spine))))))

#_
(e/defn StageUI [dd*]
  (e/client
    (let [!busy (atom false), busy (e/watch !busy), staged ($ Empty? stage), disabled? (or busy staged)]
      (dom/pre (dom/props {:style {:align-self "start"}}) (dom/text (contrib.str/pprint-str (e/as-vec (::tx dd*)))))
      (dom/button
        (dom/text "commit")
        (dom/props {:disabled disabled?, :aria-busy disabled?})
        ($ Commit (reset! !busy ($ e/TokenNofail ($ dom/On "click"))) dd*))
      (dom/button
        (dom/text "discard")
        (dom/props {:disabled disabled?, :aria-busy disabled?})
        ($ Discard (reset! !busy ($ e/TokenNofail ($ dom/On "click"))) dd*))
      (dom/pre (dom/props {:style {:align-self "start"}}) (dom/text (contrib.str/pprint-str (e/as-vec (::tx stage))))))))
