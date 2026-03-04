(ns dustingetz.nav-demo-agents
  "Nav demo agent registry.
   - Dev:  dev.cljc calls load-all-sitemaps, passes to agent/serve!
   - Prod: prod.cljc calls connect-all! to register with cloud proxy")

(def demos
  [{:ns-sym 'dustingetz.nav-weather      :agent-id "weather"}
   #_{:ns-sym 'dustingetz.nav-jvm           :agent-id "jvm"}
   {:ns-sym 'dustingetz.nav-jvm-deep      :agent-id "jvm-deep"}
   #_{:ns-sym 'dustingetz.nav-processes     :agent-id "processes"}
   #_{:ns-sym 'dustingetz.nav-git-explorer  :agent-id "git"}
   {:ns-sym 'dustingetz.nav-bgp           :agent-id "bgp"}
   {:ns-sym 'dustingetz.nav-disk          :agent-id "disk"}
   #_{:ns-sym 'dustingetz.nav-maven         :agent-id "maven"}
   {:ns-sym 'dustingetz.nav-satellites    :agent-id "satellites"}
   {:ns-sym 'dustingetz.nav-earthquake    :agent-id "earthquakes"}
   #_{:ns-sym 'dustingetz.nav-wikipedia     :agent-id "wikipedia"}])

(defn load-all-sitemaps
  "Require all demo namespaces and merge their sitemaps."
  []
  (reduce (fn [acc {:keys [ns-sym]}]
            (require ns-sym)
            (merge acc (deref (ns-resolve ns-sym 'sitemap))))
    {} demos))

(defn connect-all!
  "Connect each demo as a separate agent to an existing proxy.
   Called from prod.cljc with the cloud proxy URL."
  [connect-fn proxy-base-url qualify-fn]
  (doseq [{:keys [ns-sym agent-id]} demos]
    (try
      (require ns-sym)
      (let [sm (deref (ns-resolve ns-sym 'sitemap))]
        (connect-fn (str proxy-base-url "?id=" (qualify-fn agent-id))
                    sm nil :regional agent-id))
      (catch Exception e
        (println (format "Failed to connect '%s': %s" agent-id (.getMessage e)))))))
