(ns agents.ports
  (:require
   [hyperfiddle.electric :as e]
   [missionary.core :as m]))

;;; Ports
(defonce !ports (atom {}))

(comment
  @!ports
  )

(e/def ports (e/watch !ports))

(defn add-port [ports fsym F] (assoc ports fsym {::F F}))
(defn remove-port [ports fsym] (dissoc ports fsym))

(defn run-port [ports fsym args]
  (let [!result (atom nil)]
    (update ports fsym assoc-in [::instances args] {::!result !result, ::signal (m/signal (m/watch !result))})))

(defn stop-port [ports fsym args] (update ports fsym update ::instances dissoc args))

(e/defn Register [fsym F]
  (swap! !ports add-port fsym F)
  (e/on-unmount #(swap! !ports remove-port fsym))
  (e/for-by key [[args {::keys [!result]}] (get-in ports [fsym ::instances])]
    (try (reset! !result (e/apply F args))
         (catch hyperfiddle.electric.Pending _))))

(e/defn Call [fsym args]
  (swap! !ports run-port fsym args)
  (e/on-unmount #(swap! !ports stop-port fsym args))
  (when-some [signal> (get-in ports [fsym ::instances args ::signal])]
    (new signal>)))

