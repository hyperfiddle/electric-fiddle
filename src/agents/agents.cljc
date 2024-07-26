(ns agents.agents
  (:require
   [agents.ports :as ports]
   [hyperfiddle.electric :as e]
   [hyperfiddle.electric-local-def :as local]
   [missionary.core :as m]))

(defonce !agents (atom {})) ; {agent-id agent-info-map} - shared state

(defn add-agent! [functions metadata]
  (let [id (or (::id metadata) (random-uuid))]
    (swap! !agents assoc id (assoc metadata ::functions functions ::id id))
    (doseq [[fsym f>] functions]
      (swap! ports/!ports ports/add-port fsym f>))))

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
        (ports/Register. fsym (e/fn [args] (e/client (ports/Call. fsym args))))))))

(local/defn RunAgents []
  (let [ports (e/watch ports/!ports)]
    (e/for-by key [[_id agent] (e/watch !agents)]
      (e/for-by key [[fsym f] (::functions agent)]
        (prn "will register local" fsym "for" f)
        (ports/RegisterLocal. fsym f)))))

;;; Interop helpers

(defn once ; (once (call #(System/getProperties)))
  ([task] (once task ::ports/init))
  ([task init] (m/reductions {} init (m/ap (m/? task)))))

(defn call
  ([f] (m/sp (f)))
  ([f args] (m/sp (apply f args))))

(defn every
  "Return a flow running `task` every `ms` milliseconds, with optional initial value `init` (default to nil)."
  ([ms task] (every ms task ::ports/init))
  ([ms task init]
   (->> (m/ap
          (loop []
            (m/amb (m/? task)
              (do (m/? (m/sleep ms))
                  (recur)))))
     (m/reductions {} init))))

(local/defn ConnectElectric [F]
  (let [F (ports/InjectArgs. F)]
    (fn [] F)))
