`e/def` removed.
* e/def has been removed in v3 for internal reasons - we think its not needed
* if you really really need a global singleton signal, you can use missionary
* you can replace many of e/def usages with binding at the root of the app:
```
;; before
(e/def foo 1)

;; now
(declare foo)
(e/defn App [ring-req]
  (binding [foo 1]
    ...))
```


What’s the result of using a declare that you forgot to bind?
JVM: java.lang.IllegalStateException: Attempting to call unbound fn: #'electric-starter-app.main/unb
Browser: Reactor failure: TypeError: can't access property "cljs$core$IFn$_invoke$arity$1", f is undefined