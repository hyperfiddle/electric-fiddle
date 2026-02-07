(ns dustingetz.nav-clojure-ns
  (:require
   [hyperfiddle.hfql2 :as hfql :refer [hfql]]
   [hyperfiddle.hfql2.protocols :as hfqlp]))

(defn doc [!x] (-> !x meta :doc))
(defn author [!x] (-> !x meta :author))
(defn var-arglists [!var] (->> !var meta :arglists seq pr-str))

(extend-type clojure.lang.Namespace
  hfqlp/Identifiable (-identify [ns] `(find-ns ~(ns-name ns)))
  hfqlp/Suggestable (-suggest [_] (hfql [ns-name doc author ns-publics meta])))

(defmethod hfqlp/-hfql-resolve `find-ns [[_ ns-sym]] (find-ns ns-sym))

(extend-type clojure.lang.Var
  hfqlp/Identifiable (-identify [var] `(find-var ~(symbol var)))
  hfqlp/Suggestable (-suggest [_] (hfql [symbol var-arglists doc meta deref])))

(defmethod hfqlp/-hfql-resolve `find-var [[_ var-sym]] (find-var var-sym))
