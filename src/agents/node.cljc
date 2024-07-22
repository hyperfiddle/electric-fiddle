(ns agents.node
  (:require
   #?(:node ["child_process" :refer [spawn]])
   [agents.agents :as agents]
   [hyperfiddle.electric :as e]
   [hyperfiddle.electric-local-def :as local]
   [missionary.core :as m]
   ))

#?(:node
   (defn top []
     (m/sp
       (let [result  (m/dfv)
             close   (m/dfv)
             process (spawn "top" #js["-l" "1"])]
         (.on (.-stdout process) "data" #(result (str %)))
         (.on (.-stderr process) "data" #(result (str "stderr:" %)))
         (.on process "close" #(close %))
         (m/? (m/race result close))))))

#?(:node
   (do
     (defonce connector nil)
     (defonce reactor nil)
     (defn start [url]
       (set! connector (binding [hyperfiddle.electric-client/*ws-server-url* url]
                         ((e/boot-client {} agents.agents/ConnectAgents nil)
                          #(js/console.log "Reactor success:" %)
                          #(js/console.error "Reactor failure:" %))))
       (agents/add-agent!
         {`OneTop  (agents/once (top))
          `FastTop (agents/every 250 (top))} ;; run top every 250ms with ::pending initial value
         {::agents/id "NodeJs"
          ::version   (.-version ^js js/process)
          ::platform  (.-platform ^js js/process)
          ::arch      (.-arch ^js js/process)})
       (set! reactor (local/run (agents/RunAgents.)))
       )

     (defn stop []
       (when reactor (reactor)) ; teardown
       (set! reactor nil))))
