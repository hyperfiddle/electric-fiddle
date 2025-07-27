(ns ^{:hyperfiddle.electric.impl.lang3/has-edef? true} ; enable server hot reloading
  dustingetz.nav-clojure-ns
  (:require #?(:clj [dustingetz.codeq-model :as q])
            [hyperfiddle.hfql0 #?(:clj :as :cljs :as-alias) hfql]))

#?(:clj (defn doc [!x] (-> !x meta :doc)))
#?(:clj (defn author [!x] (-> !x meta :author)))
#?(:clj (defn ns-publics2 [ns-sym] (vals (ns-publics ns-sym)))) ; collection-record form
#?(:clj (defn var-arglists [!var] (->> !var meta :arglists seq pr-str)))

#?(:clj (def sitemap
          (hfql/sitemap
            {all-ns (hfql/props [ns-name] {::hfql/select (ns-publics2 %)})
             ns-publics2 [symbol]
             q/list-ns-decls []
             q/list-var-codeqs []

             })))

#?(:clj (extend-type clojure.lang.Namespace
          hfql/Identifiable (-identify [ns] (ns-name ns))
          hfql/Suggestable (-suggest [_] (hfql/pull-spec [ns-name doc author ns-publics2 meta]))))

#?(:clj (extend-type clojure.lang.Var
          hfql/Identifiable (-identify [ns] (symbol ns))
          hfql/Suggestable (-suggest [_] (hfql/pull-spec [symbol var-arglists doc meta]))))