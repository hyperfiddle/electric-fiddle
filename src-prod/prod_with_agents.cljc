(ns prod-with-agents
  (:require
   #?(:clj [electric-fiddle.server-jetty :refer [start-server!]])
   agents.agents
   [hyperfiddle.electric :as e]
   prod
   ))

#?(:clj
   (defn -main [& {:strs [] :as args}] ; clojure.main entrypoint, args are strings
     (apply prod/-main args)
     (start-server!
       (eval `(fn [ring-req#] (e/boot-server {} agents.agents/ConnectAgents ring-req#))) ; (eval `…) is essential, ensuring compilation happens on boot, not during uberjar build
       (assoc prod/config :port 8081))))


