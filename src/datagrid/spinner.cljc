(ns datagrid.spinner
  (:require
   [hyperfiddle.electric-dom2 :as dom]
   [hyperfiddle.electric-svg :as svg]
   [hyperfiddle.electric :as e]
   [hyperfiddle.electric-css :as css]))

(e/def SpinnerStyle
  (e/client
    (e/share
      (e/fn* []
        (css/scoped-style
          (css/rule {:width "1rem", :height "1rem"}))))))

(e/defn* Spinner [props]
  (svg/svg (dom/props {:class [(SpinnerStyle.) "animate-spin"] ; TODO migrate to electric-css. Require @keyframes support. See definition below.
                       :aria-hidden "true"
                       :fill         "none"
                       :viewBox      "0 0 24 24"
                       :stroke-width "1.5"
                       :stroke       "currentColor"})
           (dom/props props)
           (svg/path (dom/props {:stroke-linecap  "round"
                                 :stroke-linejoin "round"
                                 :d               "M12,3 A9,9 0 1,1 7.5,19.8"}))))

;; animate-spin:

;; animation: spin 1s linear infinite;
;; @keyframes spin {
;;   from {
;;    transform: rotate(0deg);
;;   }
;;   to {
;;     transform: rotate(360deg);
;;   }
;; }
