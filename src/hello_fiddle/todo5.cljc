(ns hello-fiddle.todo5
  (:require
   [hyperfiddle.electric :as e]
   [hyperfiddle.electric-css :as css]
   [hyperfiddle.electric-dom2 :as dom]
   [datascript.core :as d]
   [missionary.core :as m])
  (:import
   (missionary Cancelled)))

(defn listen
  ([node event] (listen node event identity))
  ([node event cb]
   (m/relieve {}
     (m/reductions {} nil
       (m/observe (fn [!]
                    (let [!open? (volatile! true)
                          f     (fn [e] (when @!open?
                                          (when-some [e (cb e)]
                                            (vreset! !open? false)
                                            (! [e
                                                (fn [& _]
                                                  (vreset! !open? true)
                                                  (! [e (constantly nil) false])
                                                  nil)
                                                true]))))]
                      (.addEventListener node event f)
                      #(.removeEventListener node event f))))))))

;; (defn transact! [conn tx]
;;   #?(:clj (Thread/sleep 1000))
;;   (if (zero? (rand-int 2))
;;     {:db-before '..., :db-after '..., :tx-data tx, :tempids {}}
;;     (throw (ex-info "tx failed" {}))))

(e/defn Transaction! [id F & args]
  (let [!last-state (atom [::idle nil])
        !run?       (atom false)
        run?        (e/watch !run?)]
    ((fn [_] (when-not @!run? (reset! !run? true))) args)
    (when run?
      (let [id (e/snapshot id)
            args (e/snapshot args)]
        (try
          ((fn [_] (reset! !run? false))
           (reset! !last-state [::success id (e/apply F args)]))
          (catch hyperfiddle.electric.Pending _
            (reset! !last-state [::pending id]))
          (catch Cancelled c (throw c))
          (catch Throwable t
            ((fn [& _] (reset! !run? false))
             t
             (reset! !last-state [::failure id (ex-message t)]))))))
    (e/watch !last-state)))

(e/defn Transact!* [conn tx]
  (e/server
    (when tx
      (e/offload-task #(do (Thread/sleep 1000)
                           (d/transact! conn (if (zero? (rand-int 2))
                                               tx
                                               [[]] ;; bad tx, for demo
                                               )))))))

(e/defn Transact! [conn tx]
  (Transaction!. (hash tx) Transact!* conn tx))

(defn capture
  "Captures variablity under a thunk with stable identity.
  Return a function taking any value and always returning a stable reference thunk.
  Calling this thunk returns the given value.
  Use case: prevent unmount and remount when a cc/fn argument updates due to an inner variable dependency."
  []
  (let [!state (object-array 1)
        ret #(aget !state 0)]
    (fn [x]
      (aset !state 0 x)
      ret)))

(e/defn TxUI [status]
  (e/client
    (dom/props {:class (css/scoped-style (css/rule {:outline "2px solid gray"})
                                         (css/rule "&.pending" {:outline-color "yellow"})
                                         (css/rule "&.success" {:outline-color "green"})
                                         (css/rule "&.failure" {:outline-color "red"}))})
    (case (ignore-pendings status)
      (nil ::idle) nil
      ::pending    (dom/props {:class "pending"})
      ::success    (dom/props {:class "success"})
      ::failure    (dom/props {:class "failure"}))
    status))

(defn query-todos [db] (d/q '[:find [(pull ?e [*]) ...] :where (or [?e :todo/text] [?e :todo/checked])] db))

(e/def conn nil)
(e/def !db nil)
(e/def db nil)

(defn local-index [keyfn query-result] (contrib.data/index-by #(keyfn (first %1) %2) (map vector query-result (repeat nil))))

(defn resolve-tempid-rev [tempids id]
  (get (zipmap (vals tempids) (keys tempids)) id))

;; Tempid resolution issue: row trashing
;; Temporary todo items are trashed on tempid resolution
;; Because the key changes
;; To prevent row trashing we must generate a persistent globaly unique id (e.g. uuid)
;; Preventing us from using :db/id
;; TODO model as (Incseq (Incseq x) (Incseq dx))
(e/defn Optimistic-structure [keyfn tempids query-result] ; TODO resolve tempids
  (let [!struct (atom {})]
    (e/for-by keyfn [entity query-result]
      (let [id (keyfn entity nil)]
        (prn "new entity" id entity (resolve-tempid-rev tempids id))
        (swap! !struct update id (fn [[_entity metas]] [entity metas]))
        (when-let [tempid (resolve-tempid-rev tempids id)] (swap! !struct dissoc tempid)) ; row trashing
        )
      (e/on-unmount #(swap! !struct dissoc key)))
    [!struct (e/watch !struct)]))

(defn metas [local-index] (map second (vals local-index)))

(defn tx! [!index eid local-update-fn tx]
  (swap! !index (fn [index]
                  (update index eid
                    (fn [[entity {::keys [status] :as meta}]]
                      (let [x [(local-update-fn entity)
                               {::tempid eid, ::tx tx, ::tx-status status
                                ::original entity}]]
                        ;; #?(:cljs (js/console.log {:before [entity meta] :after x}))
                        x))))))

(defn rollback! [!index eid]
  (swap! !index (fn [index]
                  (update index eid
                    (fn [[entity {::keys [original] :as metas}]]
                      [(or original entity) (dissoc metas ::original ::tx)])))))

(defn create!
  ([!index optimistic-entity-fn tx-fn] (create! !index optimistic-entity-fn tx-fn #(str (- (Math/abs (hash (random-uuid)))))))
  ([!index optimistic-entity-fn tx-fn tempid-fn]
   (let [tempid (tempid-fn)] ; TODO inject id genesis
     (swap! !index assoc tempid [(optimistic-entity-fn tempid) {::tempid tempid
                                                                ::tx     (tx-fn tempid)}])
     tempid)))

(defmacro ignore-pendings [x]
  `(let [!x# (atom (e/snapshot ~x))]
     (reset! !x# ~x)
     (e/watch !x#)))

(e/def !tx-report) ; (atom {:tempids {}})
(e/def tx-report)

(defn optimistic-keyfn [entity metas]
  (or (:db/id entity) (::tempid metas)))

(e/defn Todo5 []
  (e/server
    (binding [conn (doto (d/create-conn)
                     (d/transact [{:db/id "-1", :todo/text "Hello"}
                                  {:db/id "-2", :todo/text "world"}]))]
      (binding [!db (atom (d/db conn))
                !tx-report (atom {:tempids {}})]
        (binding [db (e/watch !db)
                  tx-report (e/watch !tx-report)]
          (prn "report" tx-report)
          (e/client
            (let [[!index index] (Optimistic-structure. optimistic-keyfn
                                   ;; FIXME !index cannot cross the wire, tempids shouldn't either (two ways bias)
                                   (e/server (:tempids tx-report))
                                   (e/server (query-todos db)) ; FIXME can't transfer everything in virtual scroll case
                                   )]
              (e/server
                ;; (prn "metas" (filter ::tx (metas index)))
                (e/for-by ::tempid [{::keys [tempid tx]} (e/client (ignore-pendings (filter ::tx (metas index))))]
                  (let [[status _txid value] (Transact!. conn tx)
                        status (ignore-pendings status)]
                    ;; (prn tempid tx status _value)
                    (e/client
                      (case (swap! !index (fn [index] (update index tempid (fn [[entity metas]] [entity (assoc metas ::tx-status status)]))))
                        (e/server
                          (case status
                            ::success (case (do (reset! !db (d/db conn))
                                                (reset! !tx-report value))
                                        (e/client
                                          (tx! !index tempid identity nil)
                                          nil))
                            ::failure (e/client (rollback! !index tempid) nil)
                            nil)))))))
              (dom/input
                (when-let [[value done! running?] (new (listen dom/node "keypress" #(when (= "Enter" (.-key %)) (.. % -target -value))))]
                  (when running? (dom/props {:style {:color :purple}}))
                  (when (and running? value)
                    (let [done! ((capture) done!)
                          tempid (create! !index (fn [tempid] {:db/id tempid, :todo/text value})
                                   (fn [tempid] [[:db/add tempid :todo/text value]]))
                          [_ {::keys [tx-status]}] (get index tempid)]
                      (case (ignore-pendings tx-status)
                        ::pending (do (set! (.-value dom/node) "") ((done!)))
                        ;; ::success (do (swap! !index dissoc tempid) ((done!)))
                        ::failure nil
                        nil)))))
              (dom/ul
                (e/for-by (comp optimistic-keyfn second) [[_ [entity {::keys [tx tx-status] :as metas}]] (sort-by (comp optimistic-keyfn second) index)]
                  ;; (prn [entity metas])
                  (dom/li
                    (dom/input
                      (dom/props {:type :checkbox, :disabled false
                                  :class (css/scoped-style
                                           (css/rule "&.pending" {:animation "spin 1s linear infinite"})
                                           (css/keyframes "spin"
                                             (css/keyframe :from {:transform "rotate(0deg)"})
                                             (css/keyframe :to {:transform "rotate(360deg)"})))})
                      (set! (.-checked dom/node) (:todo/checked entity false))
                      (when-let [[checked? done! running?] (new (listen dom/node "change" (let [cb ((capture) #(not (:todo/checked entity)))]
                                                                                            #((cb) %))))]
                        (when running?
                          (dom/props {:style {:color :purple}
                                      :disabled true}))
                        (let [done! ((capture) done!)
                              tx    [[:db/add (:db/id entity) :todo/checked checked?]]]
                          (when running?
                            (tx! !index (:db/id entity) #(assoc % :todo/checked checked?) tx))
                          (case tx-status
                            (::success ::failure) ((done!))
                            nil)
                          (TxUI. tx-status))))
                    (dom/text (pr-str entity) " - " (pr-str tx) " - " (pr-str tx-status))
                    (when (= ::failure tx-status)
                      (dom/button
                        (dom/text "retry")
                        (when-let [[_event done! running?] (new (listen dom/node "click"))]
                          (when running? (dom/props {:border "2px yellow solid", :disabled true}))
                          (let [done! ((capture) done!)
                                tempid (::tempid metas)]
                            (create! !index (fn [tempid] {:db/id tempid, :todo/text (:todo/text entity)})
                              (fn [tempid] [[:db/add tempid :todo/text (:todo/text entity)]])
                              (fn [] tempid))))))))))))))))
