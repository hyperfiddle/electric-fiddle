(ns dustingetz.nav-clojure-ns
  (:require
   [hyperfiddle.hfql2 :as hfql :refer [hfql]]
   [hyperfiddle.hfql2.protocols :as hfqlp]))

(defn doc [!x] (-> !x meta :doc))
(defn author [!x] (-> !x meta :author))
(defn ns-publics2 [ns-sym] (vals (ns-publics ns-sym))) ; collection-record form
(defn var-arglists [!var] (->> !var meta :arglists seq pr-str))

(extend-type clojure.lang.Namespace
  hfqlp/Identifiable (identify [ns] (ns-name ns))
  hfqlp/Suggestable (suggest [_] (hfql [ns-name doc author ns-publics2 meta])))

(extend-type clojure.lang.Var
  hfqlp/Identifiable (identify [ns] (symbol ns))
  hfqlp/Suggestable (suggest [_] (hfql [symbol var-arglists doc meta])))
