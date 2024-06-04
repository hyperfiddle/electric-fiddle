(ns hello-fiddle.todo54
  (:require [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]
            [hyperfiddle.electric.impl.runtime :as r]
            [hello-fiddle.todo-style]
            [contrib.debug :as dbg]
            [datascript.core :as d]
            [missionary.core :as m]
            [hello-fiddle.stage :as stage]
            [contrib.str]
            [contrib.data]
            [clojure.string :as str])
  (:import [missionary Cancelled]
           [hyperfiddle.electric Pending]))

;;;;;;;;;;;;;;;
;; UTILITIES ;;
;;;;;;;;;;;;;;;

#?(:cljs
   (defn listen "Takes the same arguments as `addEventListener` and returns an uninitialized
  missionary flow that handles the listener's lifecycle producing `(f e)`.
  Relieves backpressure. `opts` can be a clojure map."
     ([node event-type] (listen node event-type identity))
     ([node event-type f] (listen node event-type f {}))
     ([node event-type f opts]
      (->> (m/observe (fn [!]
                        (let [! #(! (f %)), opts (clj->js opts)]
                          (.addEventListener node event-type ! opts)
                          #(.removeEventListener node event-type ! opts))))
        (m/relieve {})))))

;; FIXME stabilize `f` to prevent DOM eventListener instance trashing
(e/defn EventListener "Takes the same arguments as `addEventListener`. Returns
the result of `(f event)`.

```clj
(dom/input
  (when-some [v ($ EventListener \"input\" #(-> % .-target .-value))]
    (prn v)))
```"
  ([event-type] (new EventListener event-type identity))
  ([event-type f] (new EventListener dom/node event-type f))
  ([node event-type f] (new EventListener node event-type f {}))
  ([node event-type f opts] (new EventListener node event-type f opts nil))
  ([node event-type f opts init-v]
   (e/client (new (m/reductions {} init-v (listen node event-type f opts))))))

(defn set-releaser! [!release! v down?]
  (when-not (down? v) (compare-and-set! !release! nil #(reset! !release! nil))))

(e/defn LatchingRelay
  ([v] (new LatchingRelay v nil?))
  ([v down?]
   (let [!release! (atom nil)]
     (set-releaser! !release! v down?)
     (e/watch !release!))))


(defn set-held! [!held v down?]
  (let [[_held-v release! :as held] @!held]
    (if (or release! (down? v))
      held
      (compare-and-set! !held held [v #(swap! !held assoc 1 nil)]))))

(e/defn DLatchingRelay
  ([v] (new DLatchingRelay v nil?))
  ([v down?]
   (let [!held (atom [(e/snapshot v) nil])]
     (set-held! !held v down?)
     (e/watch !held))))

(let [fltr (fn [pred v !ret] (when (pred v) (reset! !ret v)))]
  (e/defn Filter [pred v]
    (let [!ret (atom (e/snapshot v))]
      (fltr pred v !ret)
      (e/watch !ret))))

#?(:cljs
   (defn swap-value! [elem f & args]
     (let [v (apply f (.-value elem) args)]
       (set! (.-value elem) v)
       (.dispatchEvent elem (js/InputEvent. "input"))
       (.dispatchEvent elem (js/InputEvent. "change"))
       v)))

#?(:cljs (defn set-value! [elem v] (swap-value! elem (constantly v))))

#?(:cljs
   (defn toggle! [elem]
     (set! (.-checked elem) (not (.-checked elem)))
     (.dispatchEvent elem (js/Event. "change"))))

;;;;;;;;;;;;;;;;;
;; APPLICATION ;;
;;;;;;;;;;;;;;;;;

(e/def conn)
(e/def db)
(e/def TransactOptimistically)

(defn ->add [id [k v]] [:db/add id k v])

(defn ->todo-id [] (rand-int 9999999))
(defn ->temp-id [] (- (rand-int 999999)))

(defn query-todos [db] (d/q '[:find [(pull ?e [*]) ...] :where [?e :todo/id]] db))
(def stable-kf :todo/id)

(e/defn CreateNewInput []
  (e/client
    (dom/input
      (dom/props {:placeholder "What needs to be done?"})
      (let [txt (EventListener. "keyup" #(case (.-key %) "Enter" (-> % .-target .-value) nil))
            release! (LatchingRelay. txt)] ; FlipFlop
        (when release!
          (set! (.-value dom/node) "")
          (let [db-id (->temp-id), em {:db/id db-id, :todo/id (->todo-id), :todo/text txt,
                                       :todo/done false, :todo/created-at (inst-ms (js/Date.))}]
            (case (TransactOptimistically. em) (release!))))))))

#?(:clj
   (defn transactT [conn mp]
     (m/sp
       (m/? (m/sleep 3000))
       (try (if (some->> (:todo/text mp) (re-matches #"^x.*"))
              [::rejected "no xs here"]
              (do (m/? (m/via m/blk (d/transact! conn (mapv #(->add (:db/id mp) %) (dissoc mp :db/id)))))
                  [::accepted]))
            (catch Pending _ [::pending])
            (catch Cancelled c (throw c))
            (catch Throwable e (prn (ex-message e)) [::rejected (ex-message e)])))))

(e/defn TxUI [status]
  (e/client
    (dom/props {:class (case status (nil ::idle) nil, (name status))})))

(defn optimistic? [stable-id k optimistic]
  (some #(when (and (= stable-id (stable-kf (::data %))) (contains? (::data %) k)) %) (vals optimistic)))

(e/defn EditTodoDone [todo optimistic]
  (e/client
    (dom/input
      (dom/props {:type "checkbox"})
      (let [stored-done (:todo/done todo)
            checked (EventListener. "change" #(-> % .-target .-checked))
            [checked release!] (DLatchingRelay. checked)
            [t _v] (Filter. some?
                    (when release!
                      (let [[t :as ret]
                            (try (TransactOptimistically. (assoc (select-keys todo [:todo/id :db/id]) :todo/done checked))
                                 (catch Pending _ [::pending]))]
                        (case t ::pending ret #_else (do (release!) ret)))))
            optimistic-todo (optimistic? (stable-kf todo) :todo/done optimistic)
            pending? (= t ::pending)]
        (when-not pending? (set! (.-value dom/node) stored-done))
        (dom/props {:disabled (or optimistic-todo pending?)})
        (TxUI. (if pending? ::pending
                   (if optimistic-todo
                     (or t (::tx-status optimistic-todo) ::pending)
                     (if (= stored-done (if (some? checked) checked (.-checked dom/node))) (or t ::accepted) ::dirty))))
        #_(TxUI. (if (or optimistic? pending?) ::pending (if (= stored-done (if (some? checked) checked (.-checked dom/node))) (or t ::accepted) ::dirty)))))))

(e/defn EditTodoText [todo optimistic]
  (e/client
    (dom/input
      (dom/props {:type "text"})
      (let [stored-txt (:todo/text todo)
            txt-fn (EventListener. "keyup" #(case (.-key %)
                                              "Enter" (fn [] (-> % .-target .-value))
                                              "Escape" (do (set-value! dom/node stored-txt) nil)
                                           #_else  nil))
            ;; `Hold` isn't perfect, if user submits, edits and submits before first submit completes
            ;; the second submit is dropped
            ;; `Fork` 1 is the pattern. Will revisit in v3 with spine
            [txt-fn release!] (DLatchingRelay. txt-fn)
            [t v] (Filter. some?
                    (when release!
                      (let [[t :as ret]
                            (try (TransactOptimistically. (assoc (select-keys todo [:todo/id :db/id]) :todo/text (txt-fn)))
                                 (catch Pending _ [::pending]))]
                        (case t ::pending ret #_else (do (release!) ret)))))
            txt (EventListener. dom/node "input" #(-> % .-target .-value) {} (.-value dom/node))
            optimistic-todo (optimistic? (stable-kf todo) :todo/text optimistic)
            pending? (= t ::pending)]
        (when (and (not (dom/Focused?.)) (not pending?)) (set-value! dom/node stored-txt))
        (TxUI. (if pending? ::pending
                   (if optimistic-todo
                     (or t (::tx-status optimistic-todo) ::pending)
                     (if (= txt stored-txt) (or t ::accepted) ::dirty))))))))

(e/defn EditForm [todo optimistic]
  (e/client
    (dom/li
      (dom/props {:title (pr-str todo)})
      (EditTodoDone. todo optimistic)
      (EditTodoText. todo optimistic))))

(defn ->->id [] (let [!i (long-array [-1])] (fn [] (aset !i 0 (unchecked-inc (aget !i 0))))))

(e/defn App []
  (e/client
    (let [!optimistic (atom {}), optimistic (e/watch !optimistic)
          todos (vals (reduce (fn [ac nx] (update ac (stable-kf nx) #(merge nx %)))
                        (e/server (contrib.data/index-by (fn [x _] (stable-kf x)) (query-todos db)))
                        (mapv ::data (vals optimistic))))
          ->tx-id (->->id)]
      (binding [TransactOptimistically
                (e/fn [mp]
                  (let [tx-id
                        (or (some (fn [[k {::keys [data]}]] (when (= (stable-kf mp) (stable-kf data)) k))
                              @!optimistic)
                          (->tx-id))]
                    (swap! !optimistic update-in [tx-id ::data] merge mp)
                    (let [mp (-> @!optimistic (get tx-id) ::data)
                          [t :as ret] (e/server (new (e/task->cp (transactT conn (contrib.debug/dbg :transact mp)))))]
                      (case t
                        ::accepted (swap! !optimistic dissoc tx-id)
                        ::rejected (swap! !optimistic assoc-in [tx-id ::tx-status] t)
                        #_else nil)
                      ret)))]
        (dom/div
          (dom/props {:class "todomvc"})
          (CreateNewInput.)
          (dom/ul
            (e/for-by :todo/id [todo (sort-by :todo/created-at todos)]
              (EditForm. todo optimistic))))
        (dom/pre (dom/props {:style {:align-self :start}}) (dom/text (contrib.str/pprint-str optimistic)))))))

(e/defn Todo54 []
  (e/server
    (binding [conn (d/create-conn)]
      (binding [db (e/watch conn)]
        (e/client
          (dom/h1 (dom/text "todos"))
          (App.)
          (hello-fiddle.todo-style/Style.)
          nil)))))
