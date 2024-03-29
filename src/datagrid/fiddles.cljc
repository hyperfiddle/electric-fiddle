(ns datagrid.fiddles
  (:require
   [datagrid.datomic-browser]
   [datagrid.file-explorer]
   [datagrid.host-viewer]
   [hyperfiddle.electric :as e]
   [hyperfiddle.electric-dom2 :as dom]
   [hyperfiddle.router :as r]))

;; Dev entrypoint
;; Entries will be listed on the dev index page (http://localhost:8080)
(e/def fiddles {`datagrid.host-viewer/HostFile-Editor    datagrid.host-viewer/HostFile-Editor
                `datagrid.file-explorer/FileExplorer     datagrid.file-explorer/FileExplorer
                `datagrid.datomic-browser/DatomicBrowser datagrid.datomic-browser/DatomicBrowser})

;; Prod entrypoint, called by `prod.clj`
(e/defn FiddleMain [ring-request]
  (e/server
    (binding [e/http-request ring-request] ; make ring request available through the app
      (e/client
        (binding [dom/node js/document.body] ; where to mount dom elements
          (r/router (r/HTML5-History.)
            (let [[app & args :as route] (or (ffirst r/route) `(datagrid.file-explorer/FileExplorer))]
              (r/focus [route]
                (e/server
                  (case app
                    `datagrid.file-explorer/FileExplorer     (datagrid.file-explorer/FileExplorer. (first args))
                    `datagrid.host-viewer/HostFile-Editor    (datagrid.host-viewer/HostFile-Editor.)
                    `datagrid.datomic-browser/DatomicBrowser (datagrid.datomic-browser/DatomicBrowser.)
                    (e/client (dom/text "Not found" route))))))))))))
