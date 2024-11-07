# Forms from scratch

* Everything you need to know about forms in one page.
* Prerequisite knowledge needed:
  - basics of v3 language semantics
  - no differential dataflow understanding assumed
* Plan: We're going to build up to this form which has quite a lot going on.

!fiddle-ns[](electric-tutorial.forms-from-scratch-form/DemoFormServer1)

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

How it works
* Before we discuss forms, we first need to understand Inputs.
* Electric provides two input patterns – **synchronous circuit inputs** and **async transactional inputs**, which satisfy two different use cases.

# Synchronous inputs

* Ephemeral local state
* Very simple
* No error states, no latency

### naive low-level DOM input – uncontrolled state

!fn[](electric-tutorial.forms-from-scratch/DemoInputNaive)

* dom/on - derive a state (latest value, or signal from the FRP perspective) from a sequence of events
* recall that dom elements return their last child via implicit do

### synchronous `Input` - uncontrolled state

!fn[](electric-tutorial.forms-from-scratch/DemoInputCircuit-uncontrolled)

### synchronous `Input` – controlled state

!fn[](electric-tutorial.forms-from-scratch/DemoInputCircuit-controlled)

* Observe two `reset!` effects
* Can we unify them? Hold my beer ...

### e/amb - concurrent values in superposition

!fn[](electric-tutorial.forms-from-scratch/DemoInputCircuit-amb)

* tables - do is not sufficient
* auto-mapping

### synchronous forms

!fn[](electric-tutorial.forms-from-scratch/DemoFormSync)

* Why are they ordered? I think we're getting lucky with the reader (todo explain)
* It's just an educational demo, vector them if you want
* One problem here is the clojure data structure (map, vector etc) will destroy fine grained reactivity at element level, propagating form change batches from that point rather than individual field changes even when only one field changes. We're about to do better.

# Async transactional inputs

* server commands
* latency and failure affordances
* dirty states

### prerequisite: `e/RetryToken`

!fn[](electric-tutorial.forms-from-scratch/DemoToken)

* todo explain `e/RetryToken`
* disabled
* `::ok`
* `(t)`
* `e/Offload` - move the function to a thread pool, so Thread/sleep doesn't block the Electric server which is async. Using an async sleep here is also fine.
* failure handling?
* we call this the "Service" pattern, we will extract a reusable abstraction

### `Input!` - a transactional input w/ server

!fn[](electric-tutorial.forms-from-scratch/DemoInputServer)

!fn-src[electric-tutorial.forms-from-scratch/UserFormServer1]()

* fields are named, like the DOM `<input name="foo">`
* yellow dirty state
* red failure state, failed edits are not lost

However there are some concerns:
* How can we only commit when we are ready - we are missing a buffering primitive and submit intent
* How can we retry a failed edit - again, need a submit intent
* ... i.e., we need a *Form*

### `Form!` - transactional form with error states

Here's again the same demo as the top of the page:

!fn[](electric-tutorial.forms-from-scratch-form/DemoFormServer1)

* debug
* in flight cancellation - hit discard while commit is in flight to try to cancel the commit (you are racing the database transaction, it might not work)

!fn-src[electric-tutorial.forms-from-scratch-form/UserFormServer]()

* we use `e/amb` to collect the individual field edits
* `Form!` - introduces a *buffer* for dirty edits!
  * ... which implies `commit` and `discard` intentions, afforded as buttons
  * we retain buffered edits until the commit succeeds, or user discards
  * the form buffer is shown when `:debug true`
* `:commit` is how you merge a set of field edits into a single server command
  * <code>[[`User-form-submit id str1 num1 bool1] {id m}]</code>
  * element 0 is a command request
  * element 1 is our local prediction of the outcome of the command, presuming success - used by optimistic updates.
  * Why is the command encoded as data? Because it helps with optimistic updates, this may not be the final factoring.

Here is the command:

!fn-src[electric-tutorial.forms-from-scratch-form/User-form-submit]()

Which the service interprets and evaluates:

!fn-src[electric-tutorial.forms-from-scratch-form/UserService]()

* `guess` is ignored, this is used to implement optimistic updates which we haven't shown yet.
* **Failure handling with retry affordance** - since the form is buffering the edits, if the commit fails, we just don't clear the buffer!
* How does failure propagate from service back into the form buttons?
  * `(t ::rejected)` routes the error back to the source (e.g. the commit button) so the button can turn red (maybe put the error in a tooltip) and reset to a ready state for the user to try again.

### Inline form submit

* Sometimes, you want to submit each field individually (NOT as a form)
* e.g. iOS preferences, or settings page in a b2b saas.
* Just wrap each `Input!` field it's own `Form!`, due to the concurrent `e/amb` structure, the inline forms will interact with the server concurrently without any further effort:

!fiddle-ns[](electric-tutorial.forms3b-inline-submit/Forms3b-inline-submit)

* For this demo, we don't let you fully suppress the buttons (see: `(or (Checkbox show-buttons* :label "show-buttons") ::cqrs/smart)`), but you can just set `:show-buttons` to `false` in your app to make them go away entirely and rely only on the keyboard - which is often what you want! (For example, TodoMVC, Slack)

### Keyboard support

* Try it above! select the numeric field, `up`/`down`, `enter` to submit, `esc` to discard.
* Go to the top of the page - toggle the checkbox - esc to discard - space to toggle again - enter in any field to submit form - button turns yellow and disabled.
* Note: yes, enter in *any* field will submit *all* fields in the form.
* But - you're actually used to that! We use tab to navigate a form, and we use enter to submit it at the end.
* These are in fact the native browser semantics being exposed, i.e. we didn't implement this behavior! We just setup the DOM properly. I believe the only think we implemented is to submit when buttons are hidden, i.e. there is no `<button type="submit">` in the form.
* If you want to prevent premature submit, add validation.

### Next up - optimistic updates