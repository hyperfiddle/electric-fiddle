(ns electric-tutorial.chat2
  (:require [contrib.data :refer [index-by]]
            [contrib.str :refer [pprint-str]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [electric-tutorial.chat :refer [InputSubmit
                                            Query-todos
                                            #?(:clj !msgs)
                                            #?(:clj send-message!)]]
            [missionary.core :as m]))

(e/defn Service [effects edits]
  (e/client ; bias for writes because token doesn't transfer
    (e/for [[t id xcmd prediction] edits]
      (case (e/server
              (when-some [effect (effects xcmd)] ; secure
                (case (e/Task effect) ::ok)))
        ::ok (t)))))

(e/defn Reconcile-records [stable-kf as bs]
  (e/client
    (let [as! (e/as-vec as) ; todo differential reconciliation
          bs! (e/as-vec bs)]
      (->> (merge
             (index-by stable-kf as!)
             (index-by stable-kf bs!))
        vals
        (sort-by :t-ms)
        (drop (count bs!)) ; todo fix flicker
        (e/diff-by stable-kf)))))

(e/defn PendingController [kf xs edits]
  (let [!pending (atom {}) ; [id -> prediction]
        ps (val (e/diff-by key (e/watch !pending)))]
    (e/for [[t id xcmd prediction] edits]
      (prn 'pending-cmd xcmd)
      (swap! !pending assoc id (assoc prediction ::pending true))
      (e/on-unmount #(swap! !pending dissoc id))
      (e/amb))
    (Reconcile-records kf xs ps)))

(e/defn CrudList [kf Query List Item Create]
  (e/client
    (e/diff-by first
      (e/with-cycle [edits (e/as-vec (e/amb))] ; todo amb cycles
        (let [edits (e/diff-by first edits)
              xs (Query #_search)
              xs' (PendingController kf xs edits)]
          (e/as-vec
            (e/amb
              (List (e/fn Rows [] (e/for [x xs'] (Item x))))
              (Create (e/Count edits)))))))))

(e/defn ChatItem [x]
  (dom/li
    (dom/props {:style {:visibility (if (some? x) "visible" "hidden")
                        :background-color (when (::pending x) "yellow")}})
    (dom/text (:msg x))
    (e/amb)))

(e/defn ChatCreate [pending-count]
  (e/amb
    (e/for [[v t] (InputSubmit :placeholder "Type a message" :maxlength 100)]
      (let [id (random-uuid)
            xcmd [::send-msg id v]
            prediction {:db/id id :msg v :t-ms (.now js/Date)}] ; guess time
        [t id xcmd prediction]))
    (dom/text pending-count)))

(e/defn ChatList [Rows]
  (dom/ul (Rows)))

(def css "
.ChatView ul {
  display: grid; grid-template-rows: repeat(10, 20px);
  grid-auto-flow: dense; align-content: end; height: 200px; }")

(e/defn ChatView [!msgs]
  (e/client
    (CrudList :db/id (e/server (e/Partial Query-todos !msgs))
      ChatList
      ChatItem
      ChatCreate)))

#?(:clj (defn chat-effects [!msgs [cmd id v]] ; secure
          (case cmd
            ::send-msg (m/sp
                         (let [msg {:db/id id :msg v :t-ms (System/currentTimeMillis)}]
                           (println 'send-message msg '...)
                           (m/? (m/sleep 500))
                           (m/? (m/via m/blk (send-message! !msgs msg)))
                           (println 'success)))
            nil)))

(e/defn Stage [edits]
  (dom/pre
    (e/for [[_ _ cmd _] edits]
      (dom/text (pprint-str cmd)))
    edits))

(e/defn Chat2 []
  (e/client
    (dom/props {:class "ChatView"}) (dom/style (dom/text css))
    (Service
      (e/server (partial chat-effects !msgs))
      (Stage
        (ChatView (e/server (identity !msgs)))))))