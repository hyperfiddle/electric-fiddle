(ns agents.ports
  (:require
   [hyperfiddle.electric :as e]
   [missionary.core :as m]
   [hyperfiddle.electric-local-def :as local]))

;;; Ports
(defonce !ports (atom {}))

(comment
  @!ports
  )

(e/def ports (e/watch !ports))

(defn add-port [ports fsym f] (assoc ports fsym {::F f}))
(defn remove-port [ports fsym] (dissoc ports fsym))

(defn run-port [ports fsym call-id]
  (let [!result (atom nil)]
    (update ports fsym assoc-in [::calls call-id]
      {::!args   (atom [])
       ::!result !result,
       ::signal  (->> (m/watch !result)
                   (m/eduction (remove #{::init}))
                   (m/reductions {} nil)
                   (m/stream))})))

(defn stop-port [ports fsym args] (update ports fsym update ::calls dissoc args))

(e/defn Register [fsym F]
  (swap! !ports add-port fsym F)
  (e/on-unmount #(swap! !ports remove-port fsym))
  (e/for-by key [[_instance-id {::keys [!result !args]}] (get-in ports [fsym ::calls])]
    (try (reset! !result (new F (e/watch !args)))
         (catch hyperfiddle.electric.Pending _))))

(local/def local-ports (e/watch !ports))

(local/defn RegisterLocal [fsym f]
  (swap! !ports add-port fsym f)
  (e/on-unmount #(swap! !ports remove-port fsym))
  (e/for-by key [[_instance-id {::keys [!result !args]}] (get-in local-ports [fsym ::calls])]
    (try (let [result (new (apply f (e/watch !args)))]
           ;; (when-not (= ::init result))
           (reset! !result result))
         (catch hyperfiddle.electric.Pending _))))

(def next-call-id (partial swap! (atom 0) inc))

(e/defn Call [fsym args]
  (let [call-id (next-call-id)]
    (swap! !ports run-port fsym call-id)
    (e/on-unmount #(swap! !ports stop-port fsym call-id))
    (when-let [{::keys [!args signal]} (get-in ports [fsym ::calls call-id])]
      (prn "Call " fsym " with " args)
      (reset! !args args)
      (new signal))))

;; !ports
