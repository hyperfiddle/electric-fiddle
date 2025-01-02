# Transactional server forms <span id="title-extra"><span>

<div id="nav"></div>

* server commands / transactions / RPC (i.e., "http post")
* forms with dirty and error states with latency and failure affordances

!ns[electric-tutorial.form-service/FormsService]()

What's happening

* transactional/RPC form post, with commit/discard buttons
* controlled state bound to database record
* dirty fields turn yellow, discard to reset
* uncommitted state is *staged*, you can continue to arrange it until you submit
* on submit, commit button turns yellow while you wait
* inflight commit can be cancelled - try quickly cancelling an inflight txn before it succeeds (there is a 500ms delay)
* failed commits turn commit button red, retaining dirty state (i.e., **uncommitted state is never lost**)
* failed commits can be retried

Let's focus just on the form code itself, `UserFormServer`, repeated here:

!fn-src[electric-tutorial.form-service/UserFormServer]()

`(Input! :user/str1 str1)` - a transactional input w/ server awareness

* Inputs are named (here `:user/str1`), as with the DOM: `<input name="foo">`
* when the form submission fails (submit button turns red), the `Input!` stays yellow, because they still hold uncommitted state!

* How can we delay a commit until we are ready? `Input!` is missing a **buffering** primitive and **submit** intent
* How can we retry a failed edit? Again, `Input!` is missing a **submit** intent
* ... i.e., just `Input!` is not enough to model that. We need a **Form**

`Form!` - transactional form with error states

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
* debug flag - todo explain

Here is the command:

!fn-src[electric-tutorial.form-service/User-form-submit]()

Which the service interprets and evaluates:

!fn-src[electric-tutorial.form-service/UserService]()

* `guess` is ignored, this is used to implement optimistic updates which we haven't shown yet.
* **Failure handling with retry affordance** - since the form is buffering the edits, if the commit fails, we just don't clear the buffer!
* How does failure propagate from service back into the form buttons?
  * `(t ::rejected)` routes the error back to the source (e.g. the commit button) so the button can turn red (maybe put the error in a tooltip) and reset to a ready state for the user to try again.
