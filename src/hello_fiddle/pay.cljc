(ns hello-fiddle.pay
  (:require
   [hyperfiddle.electric :as e]
   [hyperfiddle.electric-css :as css]
   [hyperfiddle.electric-dom2 :as dom]
   [missionary.core :as m])
  (:import
   (missionary Cancelled)))

(e/def conn nil)

(defn listen [node event]
  (m/relieve {}
    (m/reductions {} nil
      (m/observe (fn [!]
                   (let [!open? (volatile! true)
                         f     (fn [e] (when @!open?
                                         (vreset! !open? false)
                                         (! [e
                                             (fn [& _]
                                               (vreset! !open? true)
                                               (! [e (constantly nil) false])
                                               nil)
                                             true])))]

                     (.addEventListener node event f)
                     #(.removeEventListener node event f)))))))

(defn transact! [conn tx]
  #?(:clj (Thread/sleep 1000))
  (if (zero? (rand-int 2))
    {:db-before '..., :db-after '..., :tx-data tx, :tempids {}}
    (throw (ex-info "tx failed" {}))))

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
      (e/offload-task #(transact! conn tx)))))

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

(e/defn TxUI [done! TxFn]
  (e/client
    (dom/props {:class (css/scoped-style (css/rule {:border "2px solid gray"})
                                         (css/rule "&.pending" {:border-color "yellow"})
                                         (css/rule "&.success" {:border-color "green"})
                                         (css/rule "&.failure" {:border-color "red"}))})
    (let [!last          (atom [::idle])        ; deal with pending trashing case branches
          _              (reset! !last (TxFn.)) ; same
          [status value] (e/watch !last)        ; same
          get-done!      ((capture) done!)]
      (case status
        ::idle    nil
        ::pending (dom/props {:class "pending"}) ; transaction submitted
        ::success (do (dom/props {:class "success"}) ((get-done!)))
        ::failure (do (dom/props {:class "failure"}) ((get-done!)))))))

(defn event->tx [e]
  [{:db/id "-1", :item/value (hash e)}])

(e/defn PayButton []
  (e/client
    (dom/button
      (dom/text "Pay")
      (when-let [[event done! running?] (new (listen dom/node "click"))]
        (when running? (dom/props {:style {:font-weight :bold}}))
        (TxUI. done! (e/fn [] (e/server (Transact!. conn (e/client (event->tx event))))))))))

