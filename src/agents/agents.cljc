(ns agents.agents
  (:require
   [agents.ports :as ports]
   [hyperfiddle.electric :as e]
   [hyperfiddle.electric-local-def :as local]
   [missionary.core :as m]))

(defonce !agents (atom {})) ; {agent-id agent-info-map} - shared state

(defn add-agent! [functions metadata]
  (swap! !agents assoc (or (::id metadata) (random-uuid)) (assoc metadata ::functions (update-vals functions (constantly nil))))
  (doseq [[fsym f>] functions]
    (swap! ports/!ports ports/add-port fsym f>)))

(defn remove-agent! [id]
  (when-let [agent (get @!agents id)]
    (doseq [fsym (::functions agent)]
      (swap! ports/!ports ports/remove-port fsym))
    (swap! !agents dissoc id)))

;;; Connector

(e/defn ConnectAgents [_ring-req]
  (e/client
    (e/for-by key [[id agent] (e/watch !agents)]
      (let [agent (update agent ::functions update-vals (constantly nil))]
        (e/server
          (swap! !agents assoc id agent)
          (e/on-unmount #(swap! !agents dissoc id)))))
    (e/for-by key [[fsym _] ports/ports]
      (e/server
        (ports/Register. fsym (e/fn [& args] (e/client (ports/Call. fsym args))))))))

(local/defn RunAgents []
  (let [ports (e/watch ports/!ports)]
    (e/for-by key [[_id agent] (e/watch !agents)]
      (e/for-by key [[fsym _] (::functions agent)]
        (let [port (get ports fsym)]
          (e/for-by key [[args {::ports/keys [!result]}] (::ports/instances port)]
            (try (reset! !result (new (::ports/F port)))
                 (catch hyperfiddle.electric.Pending _))))))))

;;; Interop helpers

(defn call [f & args] (m/sp (apply f args)))

(defn once
  ([task] (once task nil))
  ([task init] (m/reductions {} init (m/ap (m/? task)))))

(defn every ; (every 1000 (call #(System/getProperties)))
  ([ms task] (every ms task nil))
  ([ms task init]
   (->> (m/ap
          (loop [v (m/? task)]
            (m/amb v
              (recur (m/? (m/? (m/sleep ms task)))))))
     (m/reductions {} init))))

