(ns optimistic-todomvc.optimistic-todomvc
  (:require
   [contrib.missionary-contrib :as mx]
   [datascript.core :as d]
   [optimistic-todomvc.db :as db]
   [optimistic-todomvc.todo-style :as style]
   [hyperfiddle.electric-de :as e :refer [$]]
   [hyperfiddle.electric-dom3 :as dom]
   [hyperfiddle.electric.impl.lang-de2 :as lang]
   [hyperfiddle.incseq :as i]
   [missionary.core :as m]))

(let [nul #?(:clj (new Object) :cljs (new js/Object))]
  (e/defn MergeVia [kf local* global*]
    (let [S (i/spine nul)]
      (e/cursor [local local*]
        (let [k (kf (first local))]
          (S k {} local)
          ;; TODO remove local unmount
          ;; today without it the app crashes, some v3 bug
          ;; in the future the user might have a button way to delete an uncommitted/unpersisted entry
          (e/on-unmount #(S k {} nul))))
      (e/cursor [global global*]
        (let [k (kf global)]
          (S k {} [global])
          (e/on-unmount #(S k {} nul))))
      (e/join S))))

(defn ->->id [] (let [!i (long-array [0])] (fn [] (aset !i 0 (unchecked-inc (aget !i 0))))))

(defn query-todos [db] (d/q '[:find [(pull ?e [*]) ...] :where [?e :todo/id]] db))
(def stable-kf :todo/id)
(def ->todo-id (->->id))
(def ->tempid (->->id))

;;;;;;;;;;;;;;;;
;; CREATE NEW ;;
;;;;;;;;;;;;;;;;

#?(:cljs (defn slurp! [nd] (let [v (.-value nd)] (set! (.-value nd) "") v)))

#?(:cljs
   (defn event->text [e]
     (let [nd (.-target e)]
       (case (.-key e)
         "Enter"  (not-empty (slurp! nd))
         "Escape" (do (.blur nd) (set! (.-value nd) nil))
         #_else   nil))))

#?(:cljs
   (defn event->entity [e]
     (when-some [txt (event->text e)]
       {:db/id (- (->tempid)), :todo/id (->todo-id), :todo/text txt, :todo/done false, :todo/created-at (inst-ms (js/Date.))})))

(e/defn CreateNew []
  (e/client
    (dom/input
      (dom/props {:placeholder "What needs to be done?"})
      ($ dom/On-all "keyup" event->entity))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; FORM & FIELD ABSTRACTION ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(letfn [(->form-token [!s spend spend*] (fn [[t :as tx-status]] ; FIXME bug in v3 letfn-bound syms bound this way can leak to the global scope and collide
                                          (reset! !s tx-status)
                                          (run! #(% nil) spend*)
                                          (when (not= ::db/rejected t) (spend))))]
  (e/defn Form [e ent spend ->tx Body]
    (e/client
      (let [!s (atom ($ e/Snapshot [(if spend ::db/pending ::db/idle)]))]
        (binding [db/e e, db/!status !s, db/status (e/watch !s)]
          (let [field-data ($ Body)]
            (if spend
              {::db/tx    (->tx (reduce merge ent (e/as-vec (::db/entity field-data))))
               ::db/spend (->form-token !s spend (e/as-vec (::db/spend field-data)))}
              field-data)))))))

(letfn [(->field-token [!s spend] (fn [tx-status] (reset! !s tx-status) (spend)))] ; FIXME bug in v3 letfn-bound syms bound this way can leak to the global scope and collide
  (e/defn Field [a v ->tx Body]
    (let [!s (atom [::db/idle])]
      (binding [db/!status !s, db/status (e/watch !s), db/a a, db/v v]
        (let [[nx spend] ($ Body)]
          (if spend
            ({} (reset! !s [::db/pending])
             {::db/entity {a nx}, ::db/tx (->tx db/e a nx), ::db/spend (->field-token !s spend)})
            (e/amb)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; GENERIC UI COMPONENTS ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;

(e/defn TxUI [status]
  (e/client
    (dom/props {:class (case status (nil ::db/idle) nil, (name status))})))

(letfn [(checked [e] (-> e .-target .-checked))]
  (e/defn Checkbox []
    (e/client
      (dom/input
        (dom/props {:type "checkbox"})
        (let [[t ?e] db/status]
          (case t ::db/pending nil #_else (set! (.-checked dom/node) db/v))
          ($ TxUI t)
          (dom/props {:title (when (= ::db/rejected t) ?e)})
          (let [nx ($ dom/On "change" checked)]
            (if-some [spend ($ e/TokenNofail nx)]  [nx spend]  (e/amb))))))))

#?(:cljs (defn input-commit> [nd]
           (->> (mx/mix
                  (m/observe (fn [!] (dom/with-listener nd "keyup"
                                       (fn [e] (when (= "Enter" (.-key e)) (! [e (.-value nd)]))))))
                  (m/observe (fn [!] (dom/with-listener nd "blur" (fn [e] (! [e (.-value nd)]))))))
             (m/reductions {} nil)
             (m/relieve {}))))

(letfn [(escape [nd !x v e]
          (when (= "Escape" (.-key e))
            (reset! !x v)  (set! (.-value nd) v) (.blur nd)))]
  (e/defn InputValue [v]
    (let [!x (atom ($ e/Snapshot v)), x (e/watch !x)]
      ($ dom/On "input" #(reset! !x (-> % .-target .-value)))
      ($ dom/On "keyup" (partial escape dom/node !x v))
      (when-not ($ dom/Focused?) (set! (.-value dom/node) x))
      x)))

(letfn [(real? [e nx v t] (if (= ::db/rejected t) (not= "blur" (.-type e)) (not= nx v)))]
  (e/defn Input []
    (e/client
      (dom/input
        (let [[t ?ex] db/status
              [e nx]  (e/input (input-commit> dom/node))
              txt     ($ InputValue (or nx db/v))]
          ($ TxUI (case t (::db/pending ::db/rejected) t #_else (if (= txt db/v) t ::db/dirty)))
          (dom/props {:title (when (= ::db/rejected t) (pr-str ?ex)), :type "text"})
          (when-some [spend ($ e/Token e)]
            (if ($ e/Snapshot (real? e nx db/v t))  [nx spend]  (spend nil))))))))

;;;;;;;;;
;; APP ;;
;;;;;;;;;

(defn entity->tx [ent] (into [] (map (fn [[k v]] [:db/add (:db/id ent) k v])) (dissoc ent :db/id)))
(defn av->tx [e a v] [[:db/add e a v]])

(e/defn App []
  (e/client
    (dom/div
      (dom/props {:class "todomvc"})
      (let [data ($ MergeVia stable-kf
                    ($ CreateNew)
                    (e/server (e/diff-by stable-kf ($ e/Offload #(query-todos db/db)))))]
        (dom/ul
          (e/cursor [[ent spend] data]
            ($ Form (:db/id ent) ent spend entity->tx
               (e/fn []
                 (dom/li
                   (let [[t ?e] db/status]
                     ($ TxUI t)
                     (dom/props {:title (pr-str (if (= ::db/rejected t) ?e ent))}))
                   (e/amb
                     ($ Field :todo/done (:todo/done ent) av->tx Checkbox)
                     ($ Field :todo/text (:todo/text ent) av->tx Input)))))))))))

;; want to return as much info as possible
;; but if we add db or tx report into vector we cannot transfer it to the client
;; So either
;; - we return as much data as possible and filter it on the client (only status and possible ex-message for rejected?)
;; - we return as much as possible but at call site separate data so the generic info can transfer
;; - we return an e/amb instead of a vector and only read first incseq value?
;; Whatever the decision we need to reflect it in the db namespace
(def tx-fail? false)
#?(:clj (defn %persist [conn tx]
          (m/via m/blk (try (Thread/sleep 2000) ; simulated latency
                            (if tx-fail?
                              (d/transact conn [tx]) ; malformed tx
                              (d/transact! conn tx))
                            [::db/accepted]
                            (catch Throwable e [::db/rejected e])))))
#?(:clj (defn %with [db tx]
          (m/via m/blk (try [::db/accepted (:db-after (d/with db tx))] (catch Throwable e [::db/rejected e])))))

#?(:clj (def conn (d/create-conn)))

(e/defn OptimisticTodoMVC []
  (e/server
    (let [!db (atom nil)]
      (binding [db/conn (e/server (identity conn)), db/!db !db, db/db (e/watch !db)
                db/%persist (e/server (identity %persist)), db/%with (e/server (identity %with))]
        (reset! db/!db (e/watch db/conn))
        (e/client
          (dom/h1 (dom/text "todos")) ; bug in mount-point, 2 nodes under server/client don't mount
          ($ db/PersistCommitter ($ App)) ; this is auto-commit, below is branch with staging, commented
          #_($ db/Branch App)             ; TODO add commit/discard/staging-area-viewer when using this (see commented db/StageUI)
          ($ style/Style)
          nil)))))

(comment
  (def tx-fail? true)
  (def tx-fail? false)
  )
