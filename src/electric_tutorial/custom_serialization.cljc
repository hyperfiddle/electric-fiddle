(ns electric-tutorial.custom-serialization
  (:require [hyperfiddle.electric3 :as e]))

(deftype CustomType [x])

#?(:clj (defmethod print-method CustomType [obj writer]
          (.write writer "#CustomType[")
          (print-method (.-x obj) writer)
          (.write writer "]"))
   :cljs (extend-protocol IPrintWithWriter
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


(comment ; Adapt your entrypoint (bidirectional serialization is optional)
  (ns dev #_prod
    #?(:clj (:import [electric-tutorial.custom-serialization CustomType]))
    (:require [hyperfiddle.electric3 :as e]
     #?(:cljs hyperfiddle.electric3-client)
     #?(:cljs [electric-tutorial.custom-serialization :refer [CustomType]])
     cognitect.transit))

  (e/boot-server {:cognitect.transit/read-handlers {"hello-fiddle.fiddles/CustomType" (cognitect.transit/read-handler (fn [[x]] (CustomType. x)))}
                  :cognitect.transit/write-handlers {CustomType (cognitect.transit/write-handler (constantly "hello-fiddle.fiddles/CustomType") (fn [obj] [(.-x obj)]))}}
    ProdMain (e/server ring-req))

  (e/boot-client {:cognitect.transit/read-handlers {"hello-fiddle.fiddles/CustomType" (cognitect.transit/read-handler (fn [[x]] (CustomType. x)))}
                  :cognitect.transit/write-handlers {CustomType (cognitect.transit/write-handler (constantly "hello-fiddle.fiddles/CustomType") (fn [obj] [(.-x obj)]))}}
    ProdMain (e/server nil)))