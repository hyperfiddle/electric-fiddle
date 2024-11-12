(ns dustingetz.metaobject
  "https://github.com/NikolaySuslov/electric-objmodel"
  (:require [contrib.assert :refer [check]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.incseq :as i]))

(e/defn Some [xs] (first (e/as-vec xs)))

(e/defn Vtable-lookup [vtable mname]
  (let [Fs (e/for [[mname' F] vtable]
             (e/When (= mname mname') F))]
    (Some Fs)))

(e/defn MetaObject [] ; bootstraped Object
  (let [!vtable (i/spine) vtable (e/join !vtable)] ; vtable is a reactive list of kvs
    (!vtable ::add-method {} [::add-method (e/fn Add-method [Self mname F] ((Self ::get-!vtable) mname {} [mname F]))])
    (!vtable ::get-method {} [::get-method (e/fn Get-method [Self mname] (Vtable-lookup (Self ::get-vtable) mname))])
    (e/fn Self [mname & args]
      (case mname
        ::get-vtable vtable ::get-!vtable !vtable ; hack
        (e/Apply (Vtable-lookup vtable mname) Self args)))))

(e/defn Objekt ; java.lang.Object collision
  ([] (Objekt (MetaObject)))
  ([Super]
   (let [!vtable (i/spine)
         vtable (e/amb (e/join !vtable) (Super ::get-vtable))]
     (e/fn Self [mname & args]
       (case mname
         ::get-vtable vtable ::get-!vtable !vtable ; hack
         (e/Apply (Vtable-lookup vtable mname) Self args))))))

(declare css)
(e/defn DemoMetaobject []
  (dom/h1 (dom/text "DemoMetaobject (see console)"))
  (dom/style (dom/text css))
  (let [O (Objekt)]
    (dom/dl
      (dom/dt (dom/text "methods"))
      (dom/dd (dom/props {:class "methods"})
        (e/for [[mname F] (O ::get-vtable)]
          (dom/pre (dom/text (pr-str mname) " " F))))
      ; statements, not too fast
      (let [n (dom/dt (dom/button (dom/text "next")
                        (dom/On "click" (partial (fn [!n e] (swap! !n inc)) (atom 0)) 0)))]
        (dom/dd
          (dom/text n " "
            (case n
              0 (check some? (e/as-vec (O ::get-vtable)))
              1 (O ::add-method :prn (e/fn Prn [Self x] (prn 'Prn x)))
              2 (check some? (Vtable-lookup (O ::get-vtable) :prn))
              3 (check some? (O ::get-method :prn))
              4 (check nil? (e/call (O ::get-method :prn) O 42))
              5 (check nil? (O :prn 42))
              6 (O ::add-method :prn2 (e/fn Prn2 [Self x] (Self :prn (inc x))))
              7 (O :prn2 42)
              ::done)))))))

(def css "
dl { margin: 0; display: grid; grid-template-columns: max-content auto; }
dt { grid-column: 1; }
dd { grid-column: 2; margin-left: 1em; margin-bottom: .5em; }
dd.methods { height: 10em; }")