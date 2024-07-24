(ns agents.node
  (:require
   #?(:node ["child_process" :refer [spawn]])
   [agents.agents :as agents]
   [hyperfiddle.electric :as e]
   [hyperfiddle.electric-local-def :as local]
   [missionary.core :as m]))

#?(:node
   (defn top
     ([] (top -1))
     ([max-lines]
      (m/sp
        (let [process-args  (cond-> ["-l" "1"]
                              (>= max-lines 0) (conj "-n" (str max-lines)))
              process       (spawn "top" (clj->js process-args))
              result-chunks #js []
              result        (m/dfv)]
          (.on (.-stdout process) "data" #(.push result-chunks (str %)))
          (.on (.-stderr process) "data" #(.push result-chunks (str "stderr:" %)))
          (.on process "close" #(result (.join result-chunks "")))
          (m/? result))))))

#?(:node (defn once-top [max-lines] (agents/once (top max-lines))))
#?(:node (defn live-top [ms max-lines] (agents/every ms (top max-lines))))

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
         {`OnceTop once-top
          `LiveTop live-top} ;; run top every 250ms with ::pending initial value
         {::agents/id    "NodeJs"
          ::agents/specs {`OnceTop {::agents/args [[:max-lines {:type :int, :default 5}]]}
                          `LiveTop {::agents/args [[:refresh-rate {:type :int, :default 2000}]
                                                   [:max-lines {:type :int, :default 5}]]}}
          ::version      (.-version ^js js/process)
          ::platform     (.-platform ^js js/process)
          ::arch         (.-arch ^js js/process)})
       (set! reactor (local/run (agents/RunAgents.))))

     (defn stop []
       (when reactor (reactor)) ; teardown
       (set! reactor nil)
       (when connector (connector)) ; teardown
       (set! connector nil)
       )))
