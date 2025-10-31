(ns electric-fiddle.fiddle
  (:require
    #?(:clj [clojure.java.io :as io])
    [contrib.clojurex :refer [#?(:clj slurp-safe)]]
    [electric-essay.fiddle-markdown :refer [Ns]]
    [electric-fiddle.fiddle-index :refer [NotFoundPage pages]]
    [hyperfiddle.electric3 :as e]
    [hyperfiddle.electric-dom3 :as dom]
    [hyperfiddle.router5 :as r]))

(e/defn Fiddle
  [essay-config essay-md-folder]
  (e/client
    (dom/props {:class "Tutorial"})
    (dom/style (dom/text (e/server (some-> (io/resource "electric_essay/tutorial.css") slurp-safe))))
    (let [[?fiddle & _] r/route]
      (if-not ?fiddle
        nil #_(r/ReplaceState! ['. [(first (second (first essay-config)))]])
        (Ns ?fiddle)))))
