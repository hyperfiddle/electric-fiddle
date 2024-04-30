;; - Next up:
;;  - Unify with Dustin's UI5
;;    - Abstract over dom/input (e.g. introduce Field/DomInputController)
;;    - move parallel transaction logic up, require returning dxs — try with concat in place of e/amb, but keep dxs independent!

(ns hello-fiddle.todo53
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
(e/def db nil)
(e/def !tx-report (atom {})) ; (atom {:tempids {}})
(e/def tx-report {})

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

(defn stable-kf [entity] (:db/id entity)) ; FIXME stabilize tempid->id resolution

(defn genesis [x! dx!]
  (let [tempid (- (Math/abs (hash (random-uuid))))]
    [(x! tempid) (dx! tempid)]))

(e/defn TxEmitter []
  (let [!txs (atom #{})]
    [(e/watch !txs)
     (fn emit! [tx] (swap! !txs conj tx) nil)
     (fn retract! [tx] (swap! !txs disj tx) nil)]))

(e/defn TxMonitor [tx]
  (e/server
    (let [{::keys [accepted rejected error]} tx-report]
      (cond (= accepted tx) [::accepted]
            (= rejected tx) [::rejected (ex-message error)]
            :else           nil))))

(defn optimistic [stable-kf xdxs authoritative-xs]
  (let [index (contrib.data/index-by stable-kf authoritative-xs)]
    (vals (reduce (fn [index [x dxs]]
                    (if (contains? index (stable-kf x))
                      (update index (stable-kf x) #(patch-dxs stable-kf % dxs))
                      (assoc index (stable-kf x) x)))
            index xdxs))))

(e/defn App []
  (e/client
    (let [authoritative-xs (e/server (query-todos db))
          !xdxs (atom ())
          optimistic-xs (optimistic #(:db/id %1 %2) (e/watch !xdxs) authoritative-xs)]
      (reset! !xdxs
        (concat
          (dom/input
            (when-let [[value done! running?] (new (listen dom/node "keypress" #(when (= "Enter" (.-key %)) (.. % -target -value))))]
              (when running? (dom/props {:style {:color :purple}}))
              (let [[xdxs emit! retract!] (TxEmitter.)
                    tx! (fn [tx] (emit! tx) (done!) (set! (.-value dom/node) ""))]
                (e/for-by identity [xdx xdxs]
                  (let [[status error] (TxMonitor. (second xdx))]
                    (case status
                      ::accepted (retract! xdx)
                      ::rejected (prn ::rejected xdx error)
                      nil)))
                (when (and running? value)
                  (let [xdx (genesis (fn [tempid] {:db/id tempid, :todo/text value})
                              (fn [tempid] [[:db/add tempid :todo/text value]]))]
                    (tx! xdx)))
                xdxs)))
          (dom/ul
            (apply concat
              (e/for-by :db/id ; FIXME stabilize
                  [x (sort-by :db/id optimistic-xs)]
                (dom/li
                  (let [[xdxs emit! retract!] (TxEmitter.)
                        !error (atom nil), error (e/watch !error)
                        x (reduce (partial patch-dxs :db/id) x (map second xdxs))]
                    (e/for-by identity [xdx xdxs]
                      (let [[status error] (TxMonitor. (second xdx))]
                        (case status
                          ::accepted (do (retract! xdx) (reset! !error nil))
                          ::rejected (do (retract! xdx) (reset! !error error))
                          nil)))
                    (dom/input
                      (dom/props {:type  :checkbox
                                  :class (css/scoped-style
                                           (css/rule "&.pending" {:animation "spin 1s linear infinite"})
                                           (css/keyframes "spin"
                                             (css/keyframe :from {:transform "rotate(0deg)"})
                                             (css/keyframe :to {:transform "rotate(360deg)"})))})
                      (set! (.-checked dom/node) (:todo/checked x false))
                      (TxUI. (cond (not-empty xdxs) ::pending
                                   (some? error) ::failure
                                   :else nil))
                      (when-let [[checked? done! running?] (new (listen dom/node "change" (let [cb ((capture) #(not (:todo/checked x)))]
                                                                                            #((cb) %))))]
                        (when running?
                          (dom/props {:style {:color :purple} :disabled true})
                          (case (emit! [(assoc x :todo/checked checked?) ; TODO redundant in case of edits if we have patch-dx
                                        (if (zero? (rand-int 2))
                                            [[:db/add (:db/id x) :todo/checked checked?]]
                                            [[] ;; bad tx, for demo
                                             [:db/add (:db/id x) :todo/checked checked?]]
                                            )])
                            (done!)))))
                    (dom/text (pr-str x) " - " (pr-str xdxs))
                    xdxs))))))))))

(e/defn Transactor [!tx-report conn dxs]
  (e/server
    (let [dxs (let [!x__450850__auto__ (atom (e/snapshot dxs))]
                (try (reset! !x__450850__auto__ dxs) (catch hyperfiddle.electric.Pending _))
                (e/watch !x__450850__auto__))]
      (e/for-by identity [dx dxs]
        (let [[status _txid tx-report] (Transact!. conn dx)]
          (case (ignore-pendings status)
            ::success (reset! !tx-report (assoc tx-report ::accepted dx))
            ::failure (swap! !tx-report assoc ::rejected dx, ::error tx-report)
            nil)
          nil)))))

(e/defn Todo5 []
  (e/server
    (binding [conn (d/create-conn)]
      (binding [!tx-report (atom (d/transact! conn [{:db/id "-1", :todo/text "Hello"}
                                                    {:db/id "-2", :todo/text "world"}]))]
        (binding [tx-report (e/watch !tx-report)] ; FIXME ensure views don't skip over a tx-report (e.g. m/stream)
          (binding [db (:db-after tx-report)]
            (let [xdxs (App.)
                  dxs (remove nil? (map second xdxs))]
              (Transactor. !tx-report conn dxs)
              (e/client
                (dom/pre (dom/text (contrib.str/pprint-str xdxs)))
                (dom/pre (dom/text (contrib.str/pprint-str dxs)))))))))))
