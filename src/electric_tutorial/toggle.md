# Buttons and events — Toggle

This demo toggles between client and server with a button, to introduce basic event handling idioms, in particular for remote side effects (server RPC).

!fiddle-ns[](electric-tutorial.toggle/Toggle)

What's happening
* There's a button on the frontend, with a callback, that toggles a boolean, stored in a server-side atom.
* That boolean is used to switch between a client expr and a server expr.
* Both exprs print the platform number type, which is either a `java.lang.Long` or a javascript `Number`.
* The resulting string is streamed from server to client over network, and written through to the DOM.
* The button is disabled until the event processing completes (including potential remote evaluation).

`e/Token` is the basis for transactional event processing in v3
* `e/Token` turns event callbacks into managed values with lifecycle
* Big idea is: this is a way to work with values instead of callbacks.
* A token is a resource that becomes valid when allocated, can be spent once, and is then no longer valid.
* `(e/Token event)` starts nil, and when the next event comes, allocates a new token. This token is then retained until it is spent, *ignoring and silently discarding all subsequent events*. (Fear not, the next tutorial extends this pattern for *concurrent events*.)
* When `t` becomes non-nil, `(when-some [t ...]` succeeds, and the when-body is mounted, disabling the button to prevent future events (i.e. "backpressure the user").
* Invoke the token as a function `(t)` to spend the token, which will turn it nil, which will unmount the when-body, removing the disabled state from the button, ready to receive the next event.

`(case (swap! !x not) ::ok)`
* On the server, run the server transaction (pretend the atom is a database)
* *After* that finishes, returns `::ok`.
* Because Electric's evaluation model is concurrent—Electric auto-maximizes concurrency by default—an expression like `(do (swap! !x not) ::ok)` will return `::ok` *concurrently* with running the swap!, which is not what you want! We therefore use `case` as a sequencing idiom. This is common.

Commentary on e/Token

* Many devs don't like this API when they see it, due to the exposed state machine, and want to cover it up with some syntax sugar. This was my initial reaction too.
* But what we realized as a team, is that every nontrivial UI use case actually is a state machine. We believe this is in fact the right primitive, and our success building sophisticated transactional forms with it (later in this tutorial) are evidence that this is true.
* We believe this unsugared `e/Token` primitive is quite good actually, and it will grow on you.