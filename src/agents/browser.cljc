(ns agents.browser
  (:require
   [agents.agents :as agents]
   [hyperfiddle.electric :as e]
   [hyperfiddle.electric-dom2 :as dom]
   [hyperfiddle.electric-local-def :as local]
   [missionary.core :as m]))

#?(:cljs
   (local/defn MouseCoords [& args]
     (e/client
       (prn "receive args" args)
       (dom/on! js/window "mousemove" (fn [e] {:x (.-clientX e), :y (.-clientY e)})))))


(defn echo [ms]
  (agents/every (or ms 1000) (agents/call (fn [& args] args) [ms])))

#?(:cljs
   (defn current-time [ms & args]
     (agents/every ms (agents/call (fn [] #?(:cljs (.getTime (js/Date.))))))))

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
                       (agents/add-agent! {#_#_`MouseCoords MouseCoords
                                           `Echo echo
                                           `Time current-time}
                         {::name `BrowserAgent
                          ::web-ua    (.-userAgent (.-navigator js/window))})
                       (agents/RunAgents.))))

     (defn ^:export stop []
       (when reactor (reactor)) ; teardown
       (when connector (connector)) ; teardown
       (set! reactor nil)
       (set! connector nil)
       )))

;; Dustin: instead of agents maybe we could call it process-agents (e.g. like tailscale)
