(ns hello-fiddle.todo56-staged
  (:require
   [contrib.debug]
   [contrib.missionary-contrib :as mx]
   [datascript.core :as d]
   [hello-fiddle.db :as db]
   [hello-fiddle.electronics :as el]
   [hello-fiddle.todo-style]
   [hyperfiddle.electric :as e]
   [hyperfiddle.electric-dom2 :as dom]
   [missionary.core :as m]))

;; TODO
;; - stable identity through `:db/id`
;; - sorting
;; - field & form level validation
;; - 2 TodoMVCs + common stage

;; (defn ->queue [] #?(:clj clojure.lang.PersistentQueue/EMPTY :cljs #queue []))

(defn ->->id [] (let [!i (long-array [0])] (fn [] (aset !i 0 (unchecked-inc (aget !i 0))))))

(defn query-todos [db] (d/q '[:find [(pull ?e [*]) ...] :where [?e :todo/id]] db))
(def stable-kf :todo/id)
(def ->todo-id (->->id))
(def ->tempid (->->id))

#?(:cljs (defn slurp! [nd] (let [v (.-value nd)] (set! (.-value nd) "") v)))

#?(:cljs
   (defn event->text [e]
     (let [nd (.-target e)]
       (case (.-key e)
         "Enter"  (not-empty (slurp! nd))
         "Escape" (do (.blur nd) (set! (.-value nd) nil))
         #_else   nil))))

#?(:cljs
   (defn event->create-new [e]
     (when-some [txt (event->text e)]
       {:db/id (- (->tempid)), :todo/id (->todo-id), :todo/text txt, :todo/done false, :todo/created-at (inst-ms (js/Date.))})))

(defn add-id [id !ids]
  (m/observe (fn [!] (swap! !ids conj id) (! nil) #(swap! !ids disj id))))

(e/defn CollectIDs [locals globals stable-kf]
  (let [!ids (atom #{})]
    (e/for-by first [[_ {::keys [entity]}] locals]  (swap! !ids conj (stable-kf entity)))
    (e/for-by first [[_ {::keys [entity]}] globals] (new (add-id (stable-kf entity) !ids)))
    (e/watch !ids)))

(e/defn CreateNew []
  (e/client
    (dom/input
      (dom/props {:placeholder "What needs to be done?"})
      (new el/ForkingEventListener "keyup" event->create-new))))

(defn entity->txs [e] (into [] (map (fn [[k v]] [:db/add (:db/id e) k v])) (dissoc e :db/id)))

(e/defn TxUI [status]
  (e/client
    (dom/props {:class (case status (nil ::idle) nil, (name status))})))

(let [->off (fn [!status off! off!*]
               (fn [[t :as tx-status]]
                 (reset! !status tx-status)
                 (run! #(% nil) off!*)
                 (when (not= ::rejected t) (off!))))]
  (e/defn Form [id v off! ->tx-data Body]
    (e/client
      (let [!status (atom (e/snapshot (if off! [::pending] [::idle])))
            field-data (or (new Body id v (e/watch !status)) [])]
        (if off!
          (let [!tx-data (atom v)]
            (run! #(swap! !tx-data merge %) (eduction (map ::db/x) field-data))
            [{::db/tx-data (->tx-data (e/watch !tx-data))
              ::db/off!    (->off !status off! (eduction (map ::db/off!) field-data))}])
          field-data)))))

(let [->off (fn [!status off!] (fn [tx-status] (reset! !status tx-status) (off!)))]
  (e/defn Field [id a v ->tx-data Body]
    (let [!status (atom [::idle]), [nx off!] (new Body id a v (e/watch !status))]
      (when off!
        (reset! !status [::pending])
        {::db/x {a nx} ::db/tx-data (->tx-data id a nx), ::db/off! (->off !status off!)}))))

#?(:cljs (defn input-commit> [nd]
           (->> (mx/mix
                  (m/observe (fn [!] (el/with-listener nd "keyup"
                                       (fn [e] (when (= "Enter" (.-key e)) (! [e (.-value nd)]))))))
                  (m/observe (fn [!] (el/with-listener nd "blur" (fn [e] (! [e (.-value nd)]))))))
             (m/reductions {} nil)
             (m/relieve {}))))

(let [escape (fn escape [nd !x v e]
               (when (= "Escape" (.-key e))
                 (reset! !x v) (set! (.-value nd) v) (.blur nd)))]
  (e/defn InputValue [v]
    (let [!x (atom (e/snapshot v)), x (e/watch !x)]
      (new el/On "input" #(reset! !x (-> % .-target .-value)))
      (new el/On "keyup" (partial escape dom/node !x v))
      (when-not (new dom/Focused?) (set! (.-value dom/node) x))
      x)))

(let [real? (fn real? [e nx v t] (if (= ::rejected t) (not= "blur" (.-type e)) (not= nx v)))]
  (e/defn Input [v [t ?e] Body]
    (e/client
      (dom/input
        (let [[e nx] (new (input-commit> dom/node)), txt (new InputValue (or nx v))]
          (new TxUI (case t (::pending ::rejected) t #_else (if (= txt v) t ::dirty)))
          (when ?e (dom/props {:title ?e}))
          (new Body)
          (when-some [off! (new el/Switch e)]
            (if (e/snapshot (real? e nx v t)) [nx off!] (off! nil))))))))

(e/defn Checkbox [v [t ?e] Body]
  (e/client
    (dom/input
      (dom/props {:type "checkbox"})
      (case t ::pending nil #_else (set! (.-checked dom/node) v))
      (new TxUI t)
      (when ?e (dom/props {:title ?e}))
      (new Body)
      (let [[e nx] (new el/On "change" (fn [e] [e (-> e .-target .-checked)]))]
        (when-some [off! (new el/Switch e)]
          [nx off!])))))

(defn ->add [e a v] [[:db/add e a v]])

(e/defn MasterList [local-flops globals stable-kf Body]
  (e/client
    (let [locals  (into {} (e/for-by first [[_ [e off!]] local-flops]
                             [(stable-kf e) {::entity e, ::db/off! off!}]))
          globals (into {} (e/for-by stable-kf [e globals]
                             [(stable-kf e) {::entity e}]))
          ids (new CollectIDs locals globals stable-kf)]
      (dom/pre (dom/text ids))
      (into [] cat
        (dom/ul
          (e/for-by identity [id ids]
            (let [off! (-> locals (get id) ::db/off!)
                  v    (-> (if off! locals globals) (get id) ::entity)]
              (new Body id v off!))))))))

(e/defn App []
  (e/client
    (let [txs (dom/div
                (dom/props {:class "todomvc"})
                (new MasterList (new CreateNew) (e/server (query-todos db/db)) stable-kf
                  (e/fn [id v off!]
                    (new Form id v off! entity->txs
                      (e/fn [id v [t ?e]]
                        (dom/li
                          (new TxUI t)
                          (dom/props {:title (if ?e ?e (pr-str v))})
                          (filterv some?
                            [(new Field id :todo/done (:todo/done v) ->add
                               (e/fn [id a v status]
                                 (new Checkbox v status (e/fn []))))
                             (new Field id :todo/text (:todo/text v) ->add
                               (e/fn [id a v status]
                                 (new Input v status (e/fn [] (dom/props {:type "text"})))))])))))))]
      (new db/StageUI txs)
      txs)))

(defn x-text? [[_add _e a v]] (and (= :todo/text a) (re-find #"x" v)))
(let [->id (->->id)]
  (defn unlucky-check? [x]
    (when-not (nnext x)
      (let [[[_add _e a _v]] x]
        (and (= :todo/done a) (even? (->id)))))))

(e/defn Todo56Staged []
  (e/server
    (binding [db/conn (d/create-conn), db/!db (atom nil)]
      (reset! db/!db (e/watch db/conn))
      (binding [db/db (e/watch db/!db)]
        (e/client
          (dom/h1 (dom/text "todos"))
          (db/branch App)
          (hello-fiddle.todo-style/Style.)
          nil)))))
