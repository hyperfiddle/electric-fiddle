(ns hello-fiddle.todo56
  (:require
   [contrib.debug]
   [contrib.missionary-contrib :as mx]
   [datascript.core :as d]
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

(e/def conn)
(e/def db)

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
  (m/observe (fn [!]
               (swap! !ids update id (fnil inc 0))
               (! nil)
               #(swap! !ids (fn [m] (let [n (dec (get m id))]
                                      (if (zero? n) (dissoc m id) (assoc m id n))))))))

(e/defn CollectIDs [locals globals stable-kf]
  (let [!ids (atom {})]
    (e/for-by first [[_ {::keys [entity]}] locals]  (new (add-id (stable-kf entity) !ids)))
    (e/for-by first [[_ {::keys [entity]}] globals] (new (add-id (stable-kf entity) !ids)))
    (e/watch !ids)))

(e/defn CreateNew []
  (e/client
    (dom/input
      (dom/props {:placeholder "What needs to be done?"})
      (new el/ForkingEventListener "keyup" event->create-new))))

(defn entity->txs [e] (into [] (map (fn [[k v]] [:db/add (:db/id e) k v])) (dissoc e :db/id)))

(defn x-text? [[_add _e a v]] (and (= :todo/text a) (re-find #"x" v)))
(let [->id (->->id)]
  (defn unlucky-check? [x]
    (when-not (nnext x)
      (let [[[_add _e a _v]] x]
        (and (= :todo/done a) (even? (->id)))))))

#?(:clj (defn transactT [conn tx-data]
          (m/sp (m/? (m/sleep 1000))
                (cond (some x-text? tx-data)   [::rejected "no xs here"]
                      (unlucky-check? tx-data) [::rejected "I guess it's just not your day"]
                      :else                    (do (d/transact! conn tx-data) [::accepted])))))

(e/defn Transactor [txs]
  (e/for-by identity [{::keys [tx-data done!]} txs]
    (when done!
      (let [ret (e/server (new (e/task->cp (transactT conn tx-data))))]
        (case ret (done! ret))))))

(e/defn TxUI [status]
  (e/client
    (dom/props {:class (case status (nil ::idle) nil, (name status))})))

(let [->done (fn [!status done! done!s]
               (fn [[t :as tx-status]]
                 (reset! !status tx-status)
                 (run! #(% nil) done!s)
                 (when (not= ::rejected t) (done!))))]
  (e/defn Form [id v done! ->tx-data Body]
    (e/client
      (let [!status (atom (e/snapshot (if done! [::pending] [::idle])))
            field-data (or (new Body id v (e/watch !status)) [])]
        (if done!
          (let [!tx-data (atom v)]
            (run! #(swap! !tx-data merge %) (eduction (map ::x) field-data))
            [{::tx-data (->tx-data (e/watch !tx-data))
              ::done! (->done !status done! (eduction (map ::done!) field-data))}])
          field-data)))))

(let [->done (fn [!status done!] (fn [tx-status] (reset! !status tx-status) (done!)))]
  (e/defn Field [id a v ->tx-data Body]
    (let [!status (atom [::idle]), [nx done!] (new Body id a v (e/watch !status))]
      (when done!
        (reset! !status [::pending])
        {::x {a nx} ::tx-data (->tx-data id a nx), ::done! (->done !status done!)}))))

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
      (new el/EventListener "input" #(reset! !x (-> % .-target .-value)))
      (new el/EventListener "keyup" (partial escape dom/node !x v))
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
          (when-some [done! (new el/FlipFlop e)]
            (if (e/snapshot (real? e nx v t)) [nx done!] (done! nil))))))))

(e/defn Checkbox [v [t ?e] Body]
  (e/client
    (dom/input
      (dom/props {:type "checkbox"})
      (case t ::pending nil #_else (set! (.-checked dom/node) v))
      (new TxUI t)
      (when ?e (dom/props {:title ?e}))
      (new Body)
      (let [[e nx] (new el/EventListener "change" (fn [e] [e (-> e .-target .-checked)]))]
        (when-some [done! (new el/FlipFlop e)]
          [nx done!])))))

(defn ->add [e a v] [[:db/add e a v]])

(e/defn MasterList [local-flops globals stable-kf Body]
  (e/client
    (let [locals  (into {} (e/for-by first [[_ [e done!]] local-flops]
                             [(stable-kf e) {::entity e, ::done! done!}]))
          globals (into {} (e/for-by stable-kf [e globals]
                             [(stable-kf e) {::entity e}]))
          ids (new CollectIDs locals globals stable-kf)]
      (into [] cat
        (dom/ul
          (e/for-by identity [id (keys ids)]
            (let [done! (-> locals (get id) ::done!)
                  v     (-> (if done! locals globals) (get id) ::entity)]
              (new Body id v done!))))))))

(e/defn App []
  (e/client
    (dom/div
      (dom/props {:class "todomvc"})
      (new MasterList (new CreateNew) (e/server (query-todos db)) stable-kf
        (e/fn [id v done!]
          (new Form id v done! entity->txs
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
                       (new Input v status (e/fn [] (dom/props {:type "text"})))))])))))))))

(e/defn Todo56 []
  (e/server
    (binding [conn (d/create-conn)]
      (binding [db (e/watch conn)]
        (e/client
          (dom/h1 (dom/text "todos"))
          (let [txs (App.)]
            (dom/pre (dom/props {:style {:align-self :start}}) (dom/text (contrib.str/pprint-str (mapv ::tx-data txs))))
            (new Transactor txs))
          (hello-fiddle.todo-style/Style.)
          nil)))))
