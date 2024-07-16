(ns agents.fiddles
  (:require
   [hyperfiddle.electric :as e]
   [hyperfiddle.electric-dom2 :as dom]
   [contrib.str]
   [missionary.core :as m]))

(defn ->agent [functions metadata]
  (let [!invocations (atom {})]
    {::id           (random-uuid)
     ::functions    (keys functions)
     ::!invocations !invocations
     ::invoke!       (fn [fsym]
                      (let [!result-slot (atom nil)]
                        (swap! !invocations assoc fsym !result-slot) ; TODO support passing args
                        [(m/signal (m/watch !result-slot)) (fn [] (swap! !invocations dissoc fsym))]))
     ::metadata     metadata}))

(e/defn Agent [functions GetMetadata]
  (e/server
    (let [{::keys [id !invocations] :as agent} (->agent functions (GetMetadata.))]
      (e/for-by key [[fsym !result-slot] (e/watch !invocations)]
        (try
          (reset! !result-slot (new (get functions fsym)))
          (catch hyperfiddle.electric.Pending _)))
      agent)))

(def !agents (atom {})) ; {agent-id agent-info-map} shared memory

(e/defn StoreAgent [{::keys [id] :as agent}]
  (swap! !agents assoc id agent)
  (e/on-unmount #(swap! !agents dissoc id))
  nil)

(defmacro agent [functions GetMetadata]
  `(e/fn []
     (e/server
       (new StoreAgent
         (new Agent ~functions ~GetMetadata)))))

(def !invocations (atom {})) ; shared memory

(e/defn Dashboard []
  (e/client
    (dom/h1 (dom/text "Dashboard"))
    (dom/ul
      (e/server
        (let [invocations (e/watch !invocations)]
          (e/for-by ::id [{::keys [id functions invoke! metadata] :as agent} (vals (e/watch !agents))]
            (e/client
              (dom/li
                (dom/h2 (dom/text id))
                (dom/pre (dom/text (contrib.str/pprint-str metadata)))
                (dom/h3 (dom/text "Functions"))
                (dom/ul
                  (e/server
                    (e/for-by identity [symbol functions]
                      (let [result<  (get invocations [id symbol])
                            running? (some? result<)]
                        (e/client
                          (dom/li
                            (dom/text symbol)
                            (dom/input
                              (dom/props {:type :checkbox})
                              (set! (.-checked dom/node) running?)
                              (when (dom/on! "change" #(.-checked (.-target %)))
                                (e/server
                                  (let [[result< stop!] (invoke! symbol)]
                                    (swap! !invocations assoc [id symbol] result<)
                                    (e/on-unmount #(swap! !invocations dissoc [id symbol]))))))
                            (e/server
                              (when-some [result< (get invocations [id symbol])]
                                (let [result (new result<)]
                                  (e/client
                                    (dom/pre (dom/text (contrib.str/pprint-str result)))))))))))))))))))))



(e/defn MouseCoords []
  (e/client
    (dom/on! js/window "mousemove" (fn [e] {:x (.-clientX e), :y (.-clientY e)}))))

;; Dev entrypoint
;; Entries will be listed on the dev index page (http://localhost:8080)
(e/def fiddles {`Dashboard Dashboard
                `DemoAgent (agent {`MouseCoords MouseCoords}
                             (e/fn* []
                               (e/client {::user-agent (.-userAgent (.-navigator js/window))})))})

;; Prod entrypoint, called by `prod.clj`
(e/defn FiddleMain [ring-request]
  (e/server
    (binding [e/http-request ring-request] ; make ring request available through the app
      (e/client
        (binding [dom/node js/document.body] ; where to mount dom elements
          (Dashboard.))))))
