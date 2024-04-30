(ns hello-fiddle.fiddles
  (:require
   [hyperfiddle.electric :as e]
   [hyperfiddle.electric-dom2 :as dom]
   [hello-fiddle.pay :as pay]
   ;; [hello-fiddle.todo5 :as todo5]
   ;; [hello-fiddle.todo51 :as todo51]
   ;; [hello-fiddle.todo52 :as todo52]
   [hello-fiddle.todo53 :as todo53]
   ))

(e/defn Hello []
  (e/client
    (dom/h1 (dom/text "Hello world"))))

;; Dev entrypoint
;; Entries will be listed on the dev index page (http://localhost:8080)
(e/def fiddles {`Hello Hello
                `pay/PayButton pay/PayButton
                ;; `todo5/Todo5 todo5/Todo5
                ;; `todo51/Todo5 todo51/Todo5
                ;; `todo52/Todo5 todo52/Todo5
                `todo53/Todo5 todo53/Todo5
                })

;; Prod entrypoint, called by `prod.clj`
(e/defn FiddleMain [ring-request]
  (e/server
    (binding [e/http-request ring-request] ; make ring request available through the app
      (e/client
        (binding [dom/node js/document.body] ; where to mount dom elements
          (Hello.))))))
