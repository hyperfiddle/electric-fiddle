(ns repros
  (:require
   [hyperfiddle.electric-de :as e :refer [$]]))

;; (e/defn Foo [] (prn "Foo"))


(comment
  (foo) ; := {0 [:repros/Foo 0 [] {}]}
  (type (second (first (foo)))) ; := clojure.lang.PersistentVector
  (type foo)
  (type Foo)
  )

;; Doesn't work
;; (def foo {`Foo Foo})
#_
(e/defn Entrypoint [_ring-req]
  ($ (get foo `Foo)))


;; (e/fn [] (prn "identity"))

;; {0 (:hyperfiddle.electric.impl.lang-de2/ctor (let [] (prn "identity")))}

;; Works
#_(e/defn Entrypoint [_ring-req]
    ($ (identity (e/fn [] (prn "identity")))))

;; Works
#_
(e/defn Entrypoint [_ring-req]
  ($ (identity Foo)))

#_
(defn ensure-foo
  ([F] (F))
  ([F arg] (F arg))
  ([F get deps] {:repros/Foo Foo}))

;; ?
#_
(def bar (partial ensure-foo Foo))

#_
(e/defn Entrypoint [_ring-req]
  ($ (identity bar)))


(e/defn Foo [] (prn "Foo"))
(e/defn Getfoo [] ($ Foo))
(e/defn Getfoo2 [] (prn "GetFoo2"))
;; (e/defn Entrypoint [_ring-req] (prn "8" ($ Getfoo)))
(e/defn ^:hyperfiddle.electric.impl.lang-de2/print-cljs-source Entrypoint [_ring-req] (prn "8" ($ Getfoo)))

;; #'Get-foo

;;
;; With Foo
(comment
  (def ^{:arglists (quote ([_ring-req])), :hyperfiddle.electric.impl.lang-de2/node true} Entrypoint
    (fn ([]
         (hash-map
           1
           (hyperfiddle.electric.impl.runtime-de/ctor
             :repros/Entrypoint
             0)))
      ([idx__10914__auto__]
       (case
           idx__10914__auto__
         0
         (hyperfiddle.electric.impl.runtime-de/cdef
           0
           []
           [nil]
           nil
           (fn [frame]
             (hyperfiddle.electric.impl.runtime-de/define-call
               frame
               0
               (hyperfiddle.electric.impl.runtime-de/ap
                 (hyperfiddle.electric.impl.runtime-de/pure
                   hyperfiddle.electric.impl.runtime-de/dispatch)
                 (hyperfiddle.electric.impl.runtime-de/pure 'Foo)
                 (hyperfiddle.electric.impl.runtime-de/lookup
                   frame
                   :repros/Foo
                   (hyperfiddle.electric.impl.runtime-de/pure
                     (hyperfiddle.electric.impl.runtime-de/resolve
                       frame
                       :repros/Foo)))))
             (hyperfiddle.electric.impl.runtime-de/ap
               (hyperfiddle.electric.impl.runtime-de/lookup
                 frame
                 :clojure.core/prn
                 (hyperfiddle.electric.impl.runtime-de/pure prn))
               (hyperfiddle.electric.impl.runtime-de/pure "8")
               (hyperfiddle.electric.impl.runtime-de/join
                 (hyperfiddle.electric.impl.runtime-de/call
                   frame
                   0)))))))
      ([get__10915__auto__ deps__10916__auto__] {:repros/Foo Foo}))))


;; With Getfoo on server
(comment
  (def ^{:arglists (quote ([_ring-req])), :hyperfiddle.electric.impl.lang-de2/node true} Entrypoint
    (fn ([]
         (hash-map
           1
           (hyperfiddle.electric.impl.runtime-de/ctor
             :repros/Entrypoint
             0)))
      ([idx__10914__auto__]
       (case
           idx__10914__auto__
         0
         (hyperfiddle.electric.impl.runtime-de/cdef
           0
           []
           [nil]
           nil
           (fn [frame]
             (hyperfiddle.electric.impl.runtime-de/define-call
               frame
               0
               (hyperfiddle.electric.impl.runtime-de/ap
                 (hyperfiddle.electric.impl.runtime-de/pure
                   hyperfiddle.electric.impl.runtime-de/dispatch)
                 (hyperfiddle.electric.impl.runtime-de/pure 'Getfoo)
                 (hyperfiddle.electric.impl.runtime-de/lookup
                   frame
                   :repros/Getfoo
                   (hyperfiddle.electric.impl.runtime-de/pure
                     (hyperfiddle.electric.impl.runtime-de/resolve
                       frame
                       :repros/Getfoo)))))
             (hyperfiddle.electric.impl.runtime-de/ap
               (hyperfiddle.electric.impl.runtime-de/lookup
                 frame
                 :clojure.core/prn
                 (hyperfiddle.electric.impl.runtime-de/pure prn))
               (hyperfiddle.electric.impl.runtime-de/pure "8")
               (hyperfiddle.electric.impl.runtime-de/join
                 (hyperfiddle.electric.impl.runtime-de/call
                   frame
                   0)))))))
      ([get__10915__auto__ deps__10916__auto__] {:repros/Getfoo Getfoo}))))

;; With Getfoo on client
(comment
  (clojure.core/fn
    ([]
     (clojure.core/hash-map
       1
       (hyperfiddle.electric.impl.runtime-de/ctor :repros/Entrypoint 0)))
    ([idx__10914__auto__]
     (clojure.core/case
         idx__10914__auto__
       0
       (hyperfiddle.electric.impl.runtime-de/cdef
         0
         []
         [nil]
         nil
         (clojure.core/fn
           [frame]
           (hyperfiddle.electric.impl.runtime-de/define-call
             frame
             0
             (hyperfiddle.electric.impl.runtime-de/ap
               (hyperfiddle.electric.impl.runtime-de/pure
                 hyperfiddle.electric.impl.runtime-de/dispatch)
               (hyperfiddle.electric.impl.runtime-de/pure (quote Getfoo))
               (hyperfiddle.electric.impl.runtime-de/lookup
                 frame
                 :Getfoo
                 (hyperfiddle.electric.impl.runtime-de/pure Getfoo))))
           (hyperfiddle.electric.impl.runtime-de/ap
             (hyperfiddle.electric.impl.runtime-de/lookup
               frame
               :clojure.core/prn
               (hyperfiddle.electric.impl.runtime-de/pure clojure.core/prn))
             (hyperfiddle.electric.impl.runtime-de/pure "8")
             (hyperfiddle.electric.impl.runtime-de/join
               (hyperfiddle.electric.impl.runtime-de/call frame 0)))))))
    ([get__10915__auto__ deps__10916__auto__] {})))
