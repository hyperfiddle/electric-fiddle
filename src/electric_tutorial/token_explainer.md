# `e/Token` — model latency, success, failure, retry <span id="title-extra"><span>

<div id="nav"></div>

* This demo toggles a boolean on the server, to introduce basic server RPC (remote procedure call) idioms.
* Big idea: `e/Token` turns *callbacks* into *values*.
* This primitive is the basis for transactional event processing in Electric v3.

!ns[electric-tutorial.token-explainer/TokenExplainer]()

What's happening
* There's a button on the frontend, with a callback, that toggles a boolean, stored in a server-side atom.
* Busy state – The button is yellow and disabled until the server effect completes.
* Failure state with retry affordance, when the server effect is rejected.

`e/Token` turns event callbacks into an object-like value
* object-like: it has a managed lifecycle
* value: it is immutable
* **A "token" is a resource** that becomes valid (non-nil) once allocated, can be spent once, and is then no longer valid (becomes nil).
* `(e/Token event)` starts `nil`, and when the next event comes, allocates a new token. This token is then retained until it is spent, *ignoring and silently discarding all subsequent events*. (Fear not, the next tutorial extends this pattern for *concurrent events*.)
* When `t` becomes non-nil, `(when-some [t ...]` succeeds, and the when-body is mounted, disabling the button to prevent future events **(i.e. "backpressure the user", who is the source of events)**.
* Generally you do not want to backpressure the user, except when you do.
  * (The system should always be responsive and never lose state no matter how fast the user submits events. Except when the user submitted a transaction such as "purchase" and is waiting for confirmation. The user does not wish to accidentally purchase twice!)
* Invoke the token as a function `(t)` to spend the token, which will turn it nil, which will unmount the when-body, removing the disabled state from the button, ready to receive the next event.

"Service" pattern for remote transaction processing

* The token value represents a "request"
* the request is interpreted near the app root, to trigger a server command
* The server returns `::ok` to signal success
* the client inspects the result and clears the token — either successfully or with error
* the button then becomes ready for another interaction, presenting the error if any.
* `e/Offload` - move the function to a thread pool, so Thread/sleep doesn't block the Electric server which is async. Using an async sleep here is also fine.
* We call this the `Service` pattern and may formalize a helper for it, or you can just code your own.

Commentary on `e/Token` API

* Many devs don't like this API when they see it, due to the exposed state machine, and want to cover it up with some syntax sugar. This was my initial reaction too.
* But what we realized as a team, is that every nontrivial UI use case actually is a state machine. We believe this is in fact the right primitive, and our success building sophisticated transactional forms with it (later in this tutorial) are evidence that this is true.
* We believe this unsugared `e/Token` primitive is quite good actually, and it will grow on you.