(ns hello-fiddle.todo51
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
             (reset! !last-state [::failure id t]))))))
    (e/watch !last-state)))

(e/defn Transact!* [conn tx tx-meta]
  (e/server
    (when tx
      (e/offload-task #(do (Thread/sleep 1000)
                           (d/transact! conn tx tx-meta))))))

(e/defn Transact!
  ([conn tx] (Transact!. conn tx nil))
  ([conn tx tx-meta]
   (Transaction!. (hash tx) Transact!* conn tx tx-meta)))

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

(defmacro ignore-pendings [x]
  `(let [!x# (atom (e/snapshot ~x))]
     (reset! !x# ~x)
     (e/watch !x#)))

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


;;; ------------


(defn query-todos [db] (d/q '[:find [(pull ?e [*]) ...] :where (or [?e :todo/text] [?e :todo/checked])] db))

(e/def conn nil)
(e/def !db nil)
(e/def db nil)
(e/def !tx-report) ; (atom {:tempids {}})
(e/def tx-report)

(defn resolve-tempid-rev [tempids id]
  (get (zipmap (vals tempids) (keys tempids)) id))

(defn about-entity? [stable-kf x [op e a v]]
  (and (#{:db/add :db/ret} op)
    (= (stable-kf x) e)))

(comment
  (about-entity? :db/id {:db/id 1} [:db/add 1 :foo :bar]) := true
  (about-entity? :db/id {:db/id 1} [:db/add 2 :foo :bar]) := false
  (about-entity? :db/id {:db/id 1} [:db/retractEntity 2]) := false
  )

(defn patch-dx [stable-kf x [op _e a v :as dx]]
  (if (about-entity? stable-kf x dx)
    (case op
      :db/add (assoc x a v)
      :db/ret (dissoc x a))
    x))

(comment
  (patch-dx :db/id {:db/id 1} [:db/add 1 :test/text "Hello"]) := {:db/id 1, :test/text "Hello"}
  (patch-dx :db/id {:db/id 1} [:db/add 2 :test/text "Hello"]) := {:db/id 1}
  (patch-dx :db/id {:db/id 1 :test/text "Hello"} [:db/ret 1 :test/text]) := {:db/id 1}
  (patch-dx :db/id {:db/id 1 :test/text "Hello"} [:db/ret 2 :test/text]) := {:db/id 1, :test/text "Hello"}
  )

(defn patch-dxs [stable-kf x dxs]
  (reduce (partial patch-dx stable-kf) x dxs))

(comment
  (patch-dxs :db/id {:db/id 1} [[:db/add 1 :test/text "Hello"]
                                [:db/add 1 :test/value 42]]) 
  := {:db/id 1, :test/text "Hello", :test/value 42}
  )

;; Tempid resolution issue: row trashing
;; Temporary todo items are trashed on tempid resolution
;; Because the key changes
;; To prevent row trashing we must generate a persistent globaly unique id (e.g. uuid)
;; Preventing us from using :db/id
;; TODO model as (Incseq (Incseq x) (Incseq dx))
(e/defn Optimistic-structure [stable-kf tempids authoritative-xs] ; TODO resolve tempids
  (let [!struct (atom {})]
    (e/for-by stable-kf [x authoritative-xs]
      (let [id (stable-kf x nil)]
        (prn "new entity" id (resolve-tempid-rev tempids id))
        (swap! !struct update id (fn [[_x dxs tx-report]] [x dxs tx-report]))
        (when-let [tempid (resolve-tempid-rev tempids id)] (swap! !struct dissoc tempid)) ; row trashing, not so stable
        (e/on-unmount #(swap! !struct dissoc id))))
    (let [struct (e/watch !struct)
          struct (update-vals struct (fn [[x dxs tx-report]]
                                       [(patch-dxs stable-kf x (apply concat (vals dxs)))
                                        dxs
                                        tx-report]))]
      [!struct struct])))

(defn txid [] (random-uuid))

(defn tx! [!index eid dx]
  (swap! !index update eid
    (fn [[x dxs tx-report]]
      (let [txid (txid)]
        [x (assoc dxs txid dx) tx-report]))))

(defn create!
  ([!index x! dx!] (create! !index x! dx! #(str (- (Math/abs (hash (random-uuid)))))))
  ([!index x! dx! id-genesis-fn]
   (let [tempid (id-genesis-fn)] ; TODO inject id genesis
     (doto !index
       (swap! assoc tempid [(x! tempid) {} nil])
       (tx! tempid (dx! tempid)))
     tempid)))

(defn stable-kf [entity] (:db/id entity)) ; FIXME stabilize tempid->id resolution

(defn all-dxs [index] (update-vals index second))

(defn entity-status [[x dxs tx-report]]
  (cond (not-empty dxs)                                          ::pending
        (string? tx-report) #_(instance? #?(:clj Throwable, :cljs js/Error) tx-report) ::failure
        (nil? tx-report)                                         ::idle
        :else                                                    ::success))

(e/defn Todo5 []
  (e/server
    (binding [conn (doto (d/create-conn)
                     (d/transact [{:db/id "-1", :todo/text "Hello"}
                                  {:db/id "-2", :todo/text "world"}]))]
      (binding [!db        (atom (d/db conn))
                !tx-report (atom {:tempids {}})]
        (binding [db        (e/watch !db)
                  tx-report (e/watch !tx-report)]
          (e/client
            (let [[!index index] (Optimistic-structure. stable-kf
                                   ;; FIXME !index must be on client for instant optimistic txs
                                   (e/server (:tempids tx-report))
                                   (e/server (query-todos db)) ; FIXME can't transfer everything in virtual scroll case
                                   )]
              (e/server
                (e/for-by first [[eid dxs] (e/client (ignore-pendings (all-dxs index)))]
                  (e/for-by key [[txid dx] dxs]
                    (let [[status _txid tx-report] (Transact!. conn dx)]
                      (case (ignore-pendings status)
                        ::success (case (reset! !db (d/db conn))
                                    (let [tx-report (select-keys tx-report [:tempids :tx-meta])]
                                      (reset! !tx-report tx-report)
                                      (e/client
                                        (swap! !index update eid (fn [[x dxs _tx-report]] [x (dissoc dxs txid) tx-report])) ; TxReport is not serializable
                                        nil)))
                        ::failure (let [tx-report (ex-message tx-report)]
                                    (e/client
                                      (swap! !index update eid (fn [[x dxs _tx-report]] [x (dissoc dxs txid) tx-report]))
                                      nil))
                        nil)))))
              (dom/input
                (when-let [[value done! running?] (new (listen dom/node "keypress" #(when (= "Enter" (.-key %)) (.. % -target -value))))]
                  (when running? (dom/props {:style {:color :purple}}))
                  (when (and running? value)
                    (create! !index (fn [tempid] {:db/id tempid, :todo/text value})
                      (fn [tempid] [[:db/add tempid :todo/text value]]))
                    (set! (.-value dom/node) "")
                    (done!))))
              (dom/ul
                (e/for-by (comp stable-kf first) [[x dxs tx-report :as entity] (vals (sort-by (comp stable-kf first) index))]
                  (let [status (entity-status entity)]
                    (dom/li
                      (dom/input
                        (dom/props {:type  :checkbox
                                    :class (css/scoped-style
                                             (css/rule "&.pending" {:animation "spin 1s linear infinite"})
                                             (css/keyframes "spin"
                                               (css/keyframe :from {:transform "rotate(0deg)"})
                                               (css/keyframe :to {:transform "rotate(360deg)"})))})
                        (set! (.-checked dom/node) (:todo/checked x false))
                        (TxUI. status)
                        (when-let [[checked? done! running?] (new (listen dom/node "change" (let [cb ((capture) #(not (:todo/checked x)))]
                                                                                              #((cb) %))))]
                          (when running?
                            (dom/props {:style {:color :purple} :disabled true})
                            (tx! !index (:db/id x) (if (zero? (rand-int 2))
                                                     [[:db/add (:db/id x) :todo/checked checked?]]
                                                     [[] ;; bad tx, for demo
                                                      [:db/add (:db/id x) :todo/checked checked?]]
                                                     )))
                          (let [done! ((capture) done!)]
                            (case status
                              (::success ::failure) ((done!))
                              nil))))
                      (dom/text (pr-str x) " - " (pr-str dxs)))))))))))))
