(ns electric-starter-app.main
  (:require [hyperfiddle.electric3 :as e]
            [electric-tutorial.chat-monitor]
            [hyperfiddle.electric-dom3 :as dom]))

(e/defn Main [ring-request]
  (e/client
    (binding [dom/node js/document.body
              e/http-request (e/server ring-request)]
      (dom/link (dom/props {:rel :stylesheet, :href "/tutorial.css"}))
      (electric-tutorial.chat-monitor/ChatMonitor))))
