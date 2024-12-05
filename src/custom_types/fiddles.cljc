(ns custom-types.fiddles
  (:require
   [hyperfiddle.electric-de :as e :refer [$]]
   [hyperfiddle.electric-dom3 :as dom]))

(deftype CustomType [x])

#?(:clj
   (defmethod print-method CustomType [obj writer]
     (.write writer "#CustomType[")
     (print-method (.-x obj) writer)
     (.write writer "]"))
   :cljs
   (extend-protocol IPrintWithWriter
     CustomType
     (-pr-writer [object writer opts]
       (-write writer "#CustomType[")
       (-pr-writer (.-x object) writer opts)
       (-write writer "]"))))

(e/defn CustomTypes []
  (let [client-obj (e/client (CustomType. :client))
        server-obj (e/server (CustomType. :server))]
    (e/client (prn server-obj))
    (e/server (prn client-obj))))


;; Adapt your entrypoint (bidirectional serialization is optional):
(comment
  (ns user-entrypoint
    (:require
     [hyperfiddle.electric-de :as e]
     #?(:cljs [hyperfiddle.electric-client-de])
     #?(:cljs [custom-types.fiddles :refer [CustomType]])
     cognitect.transit)
    #?(:clj (:import [custom_types.fiddles CustomType])))

  (e/boot-server {:cognitect.transit/read-handlers {"hello-fiddle.fiddles/CustomType" (cognitect.transit/read-handler (fn [[x]] (CustomType. x)))}
                  :cognitect.transit/write-handlers {CustomType (cognitect.transit/write-handler (constantly "hello-fiddle.fiddles/CustomType") (fn [obj] [(.-x obj)]))}}
    ProdMain (e/server ring-req))

  (e/boot-client {:cognitect.transit/read-handlers {"hello-fiddle.fiddles/CustomType" (cognitect.transit/read-handler (fn [[x]] (CustomType. x)))}
                  :cognitect.transit/write-handlers {CustomType (cognitect.transit/write-handler (constantly "hello-fiddle.fiddles/CustomType") (fn [obj] [(.-x obj)]))}}
    ProdMain (e/server nil)))

;; Dev entrypoint
;; Entries will be listed on the dev index page (http://localhost:8080)
(e/defn Fiddles [] {`CustomTypes CustomTypes})

;; Prod entrypoint, called by `prod.clj`
(e/defn ProdMain [_ring-request]
  (e/client
    (binding [dom/node js/document.body] ; where to mount dom elements
      ($ CustomTypes))))
