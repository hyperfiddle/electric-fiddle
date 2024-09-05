(ns electric-tutorial.forms
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]))

#?(:cljs (defn read! [maxlength node]
           (when-some [v (not-empty (subs (.-value node) 0 maxlength))]
             (set! (.-value node) "") v)))

#?(:cljs (defn submit! [maxlength e]
           (when (= "Enter" (.-key e))
             (read! maxlength (.-target e)))))

(e/defn InputSubmit [& {:keys [maxlength] :or {maxlength 100} :as props}] ; destr can cause roundtrips, fixme
  (e/client
    (dom/input (dom/props (assoc props :maxLength maxlength))
      (dom/OnAll "keydown" (partial submit! maxlength)))))

(e/defn Forms []
  )