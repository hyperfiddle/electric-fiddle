(ns leonoel.util
  #?(:cljs (:refer-clojure :exclude [Range]))
  (:require [missionary.core :as m]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-css3 :as css])
  #?(:cljs (:require-macros [leonoel.util])))

#?(:cljs (defn height [element]
           (m/relieve
             (m/observe
               (fn [!]
                 (! (.-clientHeight element))
                 (let [obs (new js/ResizeObserver
                             (fn [entries]
                               (let [entry (aget entries 0)
                                     x (if-some [box (.-contentBoxSize entry)]
                                         (.-blockSize (or (aget box 0) box))
                                         (.-height (.-contentRect entry)))]
                                 (! x))))]
                   (.observe obs element) #(.unobserve obs element)))))))

#?(:cljs (defn listen [element type->opts]
           (m/observe
             (fn [!]
               (reduce-kv (fn [_ type opts] (.addEventListener element type ! opts)) nil type->opts)
               #(reduce-kv (fn [_ type _] (.removeEventListener element type !)) nil type->opts)))))

(def invalidation (comp m/relieve (partial m/reductions (fn [r _] r))))

(defn property [element deref dirty & args]
  (->> (apply dirty element args)
    (invalidation element)
    (m/sample deref)))

(defn define [& args]
  #?(:cljs
     (comp
       (partial m/latest
         (fn [rule]
           (loop [args (seq args)]
             (if-some [[k v & args] args]
               (do (css/set-property rule k v)
                   (recur args)) rule))))
       css/make-rule<)))

(defn select* [sheet selector block]
  (apply m/latest vector ((apply juxt block) sheet selector)))

(defn select [child & block]
  (fn [sheet selector]
    (select* sheet (str selector child) block)))

(defn rules* [scope & block]
  #?(:cljs
     (m/signal
       (m/cp
         (m/?< (select* (m/?< css/stylesheet<) (str "." scope) block))
         scope))))

(defmacro defrule [sym & block]
  `(def ~sym (rules* ~(name (css/scoped-class)) ~@block)))

(e/defn Range [from to]
  ;; TODO optimize implementation
  (e/diff-by identity (range from to)))
