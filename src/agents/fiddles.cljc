(ns agents.fiddles
  (:require
   [contrib.str]
   [hyperfiddle.electric :as e]
   [hyperfiddle.electric-dom2 :as dom]
   [agents.agents :as agents]
   [agents.ports :as ports]
   [agents.browser]))

(e/defn Dashboard []
  (e/client
    (binding [dom/node js/document.body] ; where to mount dom elements
      (dom/h1 (dom/text "Dashboard"))
      (dom/ul
        (e/server
          (e/for-by ::agents/id [{::agents/keys [id functions] :as agent} (vals (e/watch agents/!agents))]
            (e/client
              (dom/li
                (dom/h2 (dom/text id))
                (dom/pre (dom/text (contrib.str/pprint-str agent)))
                (dom/h3 (dom/text "Functions"))
                (dom/ul
                  (e/server
                    (e/for-by key [[fsym _] functions]
                      (e/client
                        (dom/li
                          (dom/text fsym)
                          (when (dom/input
                                  (dom/props {:type :checkbox})
                                  ;; (set! (.-checked dom/node) running?)
                                  (dom/on! "change" #(.-checked (.-target %))))
                            (e/server
                              (let [result (ports/Call. fsym [])]
                                (e/client
                                  (dom/pre (dom/text result)))))))))))))))))))

;; Dev entrypoint
;; Entries will be listed on the dev index page (http://localhost:8080)
(e/def fiddles {`Dashboard Dashboard})

;; Prod entrypoint, called by `prod.clj`
(e/defn FiddleMain [_ring-request] (e/server (Dashboard.)))
