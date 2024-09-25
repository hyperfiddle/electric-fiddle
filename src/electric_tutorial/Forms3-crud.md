# Input! (with Stage) — transactional forms (RPC)


— Async remote transaction (RPC) with eager submit


!fiddle-ns[](electric-tutorial.forms3-crud/Forms3-crud)

* 3 states: ok/success, pending, ~~failure~~ (todo).
* Submits txn requests eagerly (as the user types) but reusing the same token instance, so that a consumer may intercept, buffer and batch the in-flight txns, for example an atomic form with commit/discard control (see **Forms** tutorial).
* (The problem with Input is - the cycle is in your database! So the entrypoint is forced into the transact! side effect.)

`Field` - you are now using our command pattern, see `::form/update`

!fn-src[hyperfiddle.input-zoo0/Input!]()

!fn-src[hyperfiddle.input-zoo0/Checkbox!]()