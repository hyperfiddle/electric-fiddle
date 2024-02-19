(ns datagrid.spinner
  (:require
   [hyperfiddle.electric-dom2 :as dom]
   [hyperfiddle.electric-svg :as svg]
   [hyperfiddle.electric :as e])
  #?(:cljs (:require-macros [datagrid.spinner :refer [spinner]])))

;; FIXME remove this macro, inline into Spinner
(defmacro spinner [& body]
  `(svg/svg (dom/props {:fill         "none"
                        :viewBox      "0 0 24 24"
                        :stroke-width "1.5"
                        :stroke       "currentColor"})
     ~@body
     (svg/path (dom/props {:stroke-linecap  "round"
                           :stroke-linejoin "round"
                           :d               "M12,3 A9,9 0 1,1 7.5,19.8"}))))

(e/defn* Spinner [props]
  (spinner (dom/props {:class "icon animate-spin" :aria-hidden "true"})
    (dom/props props)))
