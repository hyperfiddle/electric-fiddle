(ns dustingetz.file-watcher
  #?(:clj (:import (java.io File PushbackReader)))
  (:require [clojure.edn :as edn]
            #?(:clj [clojure.java.io :as io])
            #?(:clj [hawk.core :as hawk])
            [missionary.core :as m]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]))

#?(:clj
   (defn watch-file [filepath]
     (m/observe
       (fn [!]
         (let [o (hawk/watch! [{:paths [filepath]
                                :handler (fn [ctx e] (! e) ctx)}])]
           #(hawk/stop! o))))))

#?(:clj
   (defn read-edn-forms [^File file]
     (m/via m/blk
       (try
         (with-open [r (PushbackReader. (io/reader file))]
           {:status :success
            :forms  (into [] (take-while (complement #{r}))
                      (repeatedly #(edn/read {:eof r} r)))})
         (catch RuntimeException e {:status :failure :message (ex-message e)})
         (catch InterruptedException _ {:status :pending})))))

#?(:clj
   (defn watch-file-edn [filepath]
     (m/ap
       (m/amb
         {:status :pending}
         (m/? (read-edn-forms (io/file filepath)))
         (let [{:keys [file kind]} (m/?< (watch-file filepath))]
           (case kind
             :modify (m/? (read-edn-forms file))
             (m/amb)))))))

(e/defn FileWatcherDemo []
  (let [{:keys [status forms] :as m}
        (e/server (e/input (watch-file-edn "src/dustingetz/x.edn")))]
    (case status
      :success
      (dom/pre (dom/text (pr-str forms)))
      (dom/p (dom/text "not success" (pr-str m))))))