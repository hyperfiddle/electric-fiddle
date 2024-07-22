(ns agents.browser
  (:require
   [agents.agents :as agents]
   [hyperfiddle.electric :as e]
   [hyperfiddle.electric-dom2 :as dom]
   [hyperfiddle.electric-local-def :as local]))

#?(:cljs
   (local/defn MouseCoords []
     (e/client
       (dom/on! js/window "mousemove" (fn [e] {:x (.-clientX e), :y (.-clientY e)})))))

#?(:cljs
   (do
     (defonce connector nil)
     (defonce reactor nil)
     (defn ^:export start [url]
       (set! connector (binding [hyperfiddle.electric-client/*ws-server-url* url]
                         ((e/boot-client {} agents/ConnectAgents nil)
                          #(js/console.log "Reactor success:" %)
                          #(js/console.error "Reactor failure:" %))))
       (set! reactor (local/run
                       (agents/add-agent! {`MouseCoords MouseCoords}
                         {::agents/id  `BrowserAgent
                          ::user-agent (.-userAgent (.-navigator js/window))})
                       (agents/RunAgents.))))

     (defn ^:export stop []
       (when reactor (reactor)) ; teardown
       (when connector (connector)) ; teardown
       (set! reactor nil)
       (set! connector nil)
       )))

