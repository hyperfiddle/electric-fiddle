# Transactional server forms <span id="title-extra"><span>

<div id="nav"></div>

* server commands
* latency and failure affordances
* dirty states
* Plan: We're going to build up to this form which has quite a lot going on.
* prerequisite - RetryToken

!ns[electric-tutorial.form-service/DemoFormServer1]()

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

### `Input!` - a transactional input w/ server

!fn[electric-tutorial.form-explainer/FormExplainer]()

!fn-src[electric-tutorial.form-explainer/UserFormServer1]()

* fields are named, like the DOM `<input name="foo">`
* yellow dirty state
* red failure state, failed edits are not lost

However there are some concerns:
* How can we only commit when we are ready - we are missing a buffering primitive and submit intent
* How can we retry a failed edit - again, need a submit intent
* ... i.e., we need a *Form*

### `Form!` - transactional form with error states

Here's again the same demo as the top of the page:

!fn[electric-tutorial.forms-from-scratch-form/DemoFormServer1]()

* debug
* in flight cancellation - hit discard while commit is in flight to try to cancel the commit (you are racing the database transaction, it might not work)

!fn-src[electric-tutorial.form-service/UserFormServer]()

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

!fn-src[electric-tutorial.form-service/User-form-submit]()

Which the service interprets and evaluates:

!fn-src[electric-tutorial.form-service/UserService]()

* `guess` is ignored, this is used to implement optimistic updates which we haven't shown yet.
* **Failure handling with retry affordance** - since the form is buffering the edits, if the commit fails, we just don't clear the buffer!
* How does failure propagate from service back into the form buttons?
  * `(t ::rejected)` routes the error back to the source (e.g. the commit button) so the button can turn red (maybe put the error in a tooltip) and reset to a ready state for the user to try again.
