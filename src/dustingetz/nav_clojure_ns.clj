(ns dustingetz.nav-clojure-ns
  (:require [hyperfiddle.hfql0 :as hfql]))

(defn doc [!x] (-> !x meta :doc))
(defn author [!x] (-> !x meta :author))
(defn ns-publics2 [ns-sym] (vals (ns-publics ns-sym))) ; collection-record form
(defn var-arglists [!var] (->> !var meta :arglists seq pr-str))

(extend-type clojure.lang.Namespace
           hfql/Identifiable (-identify [ns] (ns-name ns))
           hfql/Suggestable (-suggest [_] (hfql/pull-spec [ns-name doc author ns-publics2 meta])))

(extend-type clojure.lang.Var
  hfql/Identifiable (-identify [ns] (symbol ns))
  hfql/Suggestable (-suggest [_] (hfql/pull-spec [symbol var-arglists doc meta])))