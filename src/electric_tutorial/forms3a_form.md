# Transactional form (remote, async, safe retry)

* `Input!` and `Form!` together coordinate a transactional server action, basically an RPC call (server function) with named arguments.
* For example, an internal tool may have a RPC endpoint for "block sub from school" and it takes N parameters.

* explicit failure handling
* Atomic/transactional submit of form derived from set of fields.

!ns[](electric-tutorial.forms3a-form/Forms3a-form)

What's happening
* transactional/RPC form post, with commit/discard buttons
* controlled state bound to database record
* dirty fields turn yellow, discard to reset
* commit button turns yellow while you wait
* inflight commit can be cancelled - try quickly cancelling an inflight txn before it succeeds (there is a 500ms delay)
* failed commits turn commit button red
* failed commits can be retried
* uncommitted state is never lost
* uncommitted state is *staged*, you can continue to arrange it until you submit

Why / what for

* Use cases: CRUD apps, business applications, internal tools — i.e., transactional database applications. Not just CRUD but also [CQRS](https://en.wikipedia.org/wiki/Command_Query_Responsibility_Segregation) (which is mostly what we actually mean when we say "CRUD" - not just inline record edits, but complex commands and queries with business rules)
* NOT for: TodoMVC, consumer apps – these do not have explicit form controls! (Or do they?)
* Which is Github's UI, or Jira? They're both! Sometimes, there's a form with dirty state and a submit button. However, many interactions submit server commands directly without the ceremony of a full form. Name the tradeoff: what is gained or lost by the presence or absence of a form?

What is a `<form>` in HTML, anyway?

* it submits an atomic payload to a server RPC endpoint
* submit is triggered by a button, or by pressing enter in a text field
* submit can fail: server can reject, network can fail, server can crash
* it coordinates with `<input type="text" name="foo">`, note `:name`

`Form!` is a buffering primitive; the buffer gets you failure handling

* `dirty` state (implies a buffer), `discard` intent, `commit` intent
* `busy` state, edits are async, cancellation of inflight commits, form is disabled until in-flight commit resolves OR is cancelled (so, form fields are not always in a  `ready` state that can accept new input)
* `failure` state, `retry` intent

How it works

* `Form!` takes a set of fields, buffers and batches them into an atomic form submission
* "Field" means: an input control that has a :name (like DOM `<input name="foo">`). We could separate out a `Field` abstraction but it seems unnecessary
* `Input!` - explain edit type and the yellow/red states


* buffered is how you get error states
  - buffer is achieved by proxy tokens on commit/discard.
  - the proxy retains the original token in memory until you commit it, that's the buffer.


`Form!`
* lets you arrange the fields into a form
* adds error handling at form level (not field level!)
* Form is doing buffering, see :debug true
* `:commit` gets only the dirty set (like HTML), reconcile it with the record in scope to get the whole form. The command is returned as a directive value `['F a b]` so that the `Service` can decide whether to run it.
* 3 states: ok/success, pending, failure (todo).
* `:debug true`  - print the stage state (dirty count and buffered edits)
* form errors and field errors are not the same thing
* once you batch the edits into a form, the error is at form level, it is difficult to take a TransactionRejected exception and map it back to an individual field, that granularity is lost
* if you want field error messages, perhaps you want each field to be its own form, i.e. inline submit? See next tutorial
* Or, perhaps you want *validation* at field level? Electric trivializes server access, the field can query as the user types to find out if e.g. the email address is not taken. You don't need to wait for the actual transaction to find out if the field is valid at field level!
* buffer and batch, commit/discard, proxy token
* Remember, the patterns are alomost entirely pure, which means most states are trivially monitored by printing the return values.
* You can think of a `Form` as a buffer. It is buffering the field state so that you can accumulate multiple field edits to stage a single form submit. Commit and discard manage the lifecycle of the buffer.
* Transaction failures route to form level. The failure is relative to the atomic transaction, field-level errors are not available. Your form validation
at field level should know if fields are valid, which is easy in electric because you can query the server to know e.g. if email address is taken without
attempting the transaction.


`Input!`
* `Input!` adds dirty and failure states needed for server transactions that can fail.
* They're designed to be composed into forms with the `Form!` pattern, which batches N fields into 1 form with atomic submit.
* Submits txn requests eagerly (as the user types) but reusing the same token instance, so that a consumer may intercept, buffer and batch the in-flight txns, for example an atomic form with commit/discard control (see **Forms** tutorial).
* This is surprising! The trick is you need to compose it with the stage
* token api gains us dirty and error state
  * [t v]
  * "edits"
* `(e/for [[t v] (Input! num1 :type "number")] [t (parse-long v)])`
* (The problem with Input is - the cycle is in your database! So the entrypoint is forced into the transact! side effect.)
* Failures can be fed back into the control, but in fact that's not happening here - we proxy the token at the stage level! Explain why this is correct

!fn-src[hyperfiddle.input-zoo0/Input!]()

* `Input!` and `Checkbox!` return `edit` structures
* `edit` structure is a `[token, value]` tuple
* `token` is a function with two arities.
  * `(token)` marks the transaction as done, releasing the dirty state of the control which will reset back to its controlled state.
  * `(token err)` marks the transaction as failed, putting the control into a red failed state so the user can submit again.
    * Note: this red failed state is not visible in the demo, because there is a Form, meaning any transactional failure is at the Form level not the Field level.
    * For individual controls to see error states, they need to be used outside of a Form!
    * (Todo: Does that even make sense anymore? The field needs a :commit hook to translate the `edit` into a transactional effect. The purpose of transactional fields is to exist in a form.)
* the numeric control has `:parse parse-long`
* ~~If you omit the `Form!` you get eager writes into the db.~~

`Service`
* accepts form submissions and interprets them for effect
* feeds result back into the form - either clearing the fields (controlled fields will reset to current database state), or on error, putting the form into retry state and leaving the uncommitted fields dirty

* form submissions rise to the root - they are concurrent
* `t` used to release state, or `(t err)` to feed the error into the commit button so it enters retry state
  * ::ok -> `(t)`, release staged form as it is safe now, which releases the fields as well
  * assert false -> retry, `(t err)`, releases commit interaction but retains staged form as it is in retry state

!fn-src[hyperfiddle.cqrs0/Service]()

Future work
  * What if the form buffer was moved to the server, so that if you refresh the page, your state is not lost?

# Scratch


* dirty state - field is yellow, edit request in flight - field level
* commit/discard buttons at form level
  - busy state - commit is disabled, edit request in flight - form level
  - cancellation - when busy, we can cancel the in-flight form edit
  - error state - at form level





  Glossary

* **Intent** refers to what the user wants to accomplish - their goal or desired action. It's about the user's purpose or motivation when interacting with an interface.
* **Affordance** refers to the perceived possibilities for action that an object or interface element provides. It's about how the design communicates what actions can be taken.
