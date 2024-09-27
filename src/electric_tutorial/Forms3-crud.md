# Transactional CRUD forms (i.e., RPC)

* This is a 3-in-1. All three form arrangements here use the exact same `Input!` controls.
* We're going to explain the `Form` pattern first, then the `Input!` controls.
* First, play around and familiarize yourself with the three forms.

!ns[](electric-tutorial.forms3-crud/Forms3-crud)

Whats happening (broadly)
* 3 forms configured slightly differently
* all attached to one database (they are *controlled* forms)
* forms are transactional - with commit/discard buttons
* yellow and red states for busy and failed
* busy transactions can be cancelled. Try quickly cancelling an inflight txn before it succeeds
* failed transactions can be retried
* uncommitted state is never lost

You can ignore `expand-tx-effects` for now, we'll come back to that.

Ok, now we will take a microscope to each of these forms.

## 1. Explicit CRUD form with transactional submit

* Use cases: CRUD apps!, i.e., internal tools which mostly just do transactions to a database. More complex business process applications as well, but only sometimes, not always.
* NOT for: consumer apps and TodoMVC – they do NOT have explicit form controls!

!ns[](electric-tutorial.forms3a-form/Forms3a-form)

What's happening

How it works
* `Form` on L13 is accumulating the field edit state
* `Field` – explain command structure
* `Input!` - explain edit type and the yellow/red states
* `Service`
* `expand-tx-effects`

!fn-src[hyperfiddle.cqrs0/Service]()


!fn-src[electric-tutorial.forms3-crud/expand-tx-effects]()

## 2. Explicit CRUD *fields* with transactional submit – demo only

* Use cases: TodoMVC item editor (well kinda, we need better UX first - stay tuned)
* This is provided for demonstration, to document the semantics of the use case, before we work on UX.

!ns[](electric-tutorial.forms3b-inline-submit/Forms3b-inline-submit)

What's happening

How it works
* Each field gets its own `Form`! `(Form (Field id :user/str1 (Input! str1)))`
* pretty cool right? Just put the form where it makes sense for the submit granularity you desire. (By the way, it composes, so you can layer them!)

## 3. ???

x !ns[](electric-tutorial.forms3c/Forms3c)


## 4. Individual CRUD *fields* that autosubmit (i.e., autosave)

* Use cases: consumer apps, TodoMVC toggle (but not TodoMVC item editor)

!ns[](electric-tutorial.forms3d-autosubmit/Forms3d-autosubmit)

What's happening

How it works
* Just omit the `Form` entirely, and you get auto-submit! Which makes sense, right?
* You can think of a `Form` as a buffer. It is buffering the field state so that you can accumulate multiple field edits to stage a single form submit. Commit and discard manage the lifecycle of the buffer.


* `Input!` adds dirty and failure states needed for server transactions that can fail.
* They're designed to be composed into forms with the `Stage!` pattern, which batches N fields into 1 form with atomic submit.


What's happening
* dirty state - field is yellow, edit request in flight - field level
* commit/discard buttons at form level
  - busy state - commit is disabled, edit request in flight - form level
  - cancellation - when busy, we can cancel the in-flight form edit
  - error state - at form level
* Transaction failures route to form level. The failure is relative to the atomic transaction, field-level errors are not available. Your form validation
at field level should know if fields are valid, which is easy in electric because you can query the server to know e.g. if email address is taken without
attempting the transaction.
* todo security discussion

Why / what for
* Atomic/transactional submit of form derived from set of fields. For example, an internal tool may have a RPC endpoint for "block sub from school" and it takes N parameters.
* Tutorial usage: here only. Notably, NOT used in TodoMVC! <a href=""></a>

`Input!`-

* Submits txn requests eagerly (as the user types) but reusing the same token instance, so that a consumer may intercept, buffer and batch the in-flight txns, for example an atomic form with commit/discard control (see **Forms** tutorial).
* This is surprising! The trick is you need to compose it with the stage
* token api gains us dirty and error state
  * [t v]
  * "edits"
* `(e/for [[t v] (Input! num1 :type "number")] [t (parse-long v)])`
* (The problem with Input is - the cycle is in your database! So the entrypoint is forced into the transact! side effect.)
* Failures can be fed back into the control, but in fact that's not happening here - we proxy the token at the stage level! Explain why this is correct

!fn-src[hyperfiddle.input-zoo0/Input!]()

`Field`

!fn-src[hyperfiddle.cqrs0/Field]()

* Field, cmd->tx, CQRS, ::cqrs/update
* basic security discussion


Stage

* `(Stage (UserForm db 42) :debug true)`
* :debug true means - print the stage state (dirty count and buffered edits)
* adds error handling at form level (not field level!)
  * form errors and field errors are not the same thingm
  * once you batch the edits into a form, the error is at form level, it is difficult to take a TransactionRejected exception and map it back to an individual field, that granularity is lost
  * if you want field error messages, perhaps you want each field to be its own form, i.e. inline submit? See next tutorial
  * Or, perhaps you want *validation* at field level? Electric trivializes server access, the field can query as the user types to find out if e.g. the email address is not taken. You don't need to wait for the actual transaction to find out if the field is valid at field level!
* buffer and batch, commit/discard, proxy token

* 3 states: ok/success, pending, ~~failure~~ (todo).
* you don't need to understand this impl, it is hairy (but short!)
* the stage is providing buffering.
* If you omit the stage, you get eager writes into the db.
* Remember, the patterns are alomost entirely pure, which means most states are trivially monitored by printing the return values.

x !fn-src[hyperfiddle.cqrs0/Stage]()

Entrypoint

* form submissions rise to the root - they are concurrent
* `t` used to release state, or `(t err)` to feed the error into the commit button so it enters retry state
  * ::ok -> `(t)`, release staged form as it is safe now, which releases the fields as well
  * assert false -> retry, `(t err)`, releases commit interaction but retains staged form as it is in retry state
