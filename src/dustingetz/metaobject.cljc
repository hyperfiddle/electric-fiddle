(ns dustingetz.metaobject
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.incseq :as i]))

(e/defn Some [xs] (first (e/as-vec xs)))

(e/defn Vtable-lookup [vtable mname]
  (let [Fs (e/for [[mname' F] vtable]
             (e/When (= mname mname') F))]
    (Some Fs)))

(e/defn MetaObject [] ; bootstraped Object
  (let [!vtable (i/spine) vtable (e/join !vtable)
        Self (e/fn Self [mname & args]
               (case mname
                 ::get-vtable vtable ; hack
                 (e/Apply (Vtable-lookup vtable mname) Self args)))]
    (!vtable ::add-method {} (e/fn [Self mname F] (!vtable mname {} F)))
    (!vtable ::get-method {} (e/fn [Self Super mname]
                               (or (Vtable-lookup (Self ::get-vtable) mname)
                                 (Super ::get-method mname))))
    Self))

(e/defn Object
  ([] (Object (MetaObject)))
  ([Super]
   (let [!vtable (i/spine) vtable (e/join !vtable)
         Get-method (Super ::get-method ::get-method)]
     (e/fn Self [mname & args]
       (case mname
         ::get-vtable vtable ; hack
         (e/Apply (Get-method Self Super mname) args))))))

(e/defn Scratch []
  (let [O (Object)]
    (O ::add-method :prn (e/fn [Self x] (prn x)))
    (O ::get-method :prn)
    (e/call (O ::get-method :prn) O 1)
    (O :prn 1)

    (O ::add-method :prn2 (e/fn [Self x] (Self :prn x)))
    (O ::get-method :prn2)
    (e/call (O ::get-method :prn2) O 2)
    (O :prn2 2)

    (let [O2 (Object O)]
      (O ::get-method :prn)
      (O2 :prn)
      (O2 ::get-method :prn2)
      (O2 :prn2))))