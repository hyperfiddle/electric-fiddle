# Simple chat app with optimistic updates <span id="title-extra"><span>

<div id="nav"></div>

* A multiplayer chat app with auth and presence, all in one file.
* When multiple sessions are connected, you can see who else is present.
* Try two tabs

!ns[electric-tutorial.chat-monitor/ChatMonitor]()

What's happening

* Chat messages are stored in an atom on the server
* It's multiplayer, each connected session sees the same messages
* Auth – log in (empty password)
* Presence – logged in users can see who else is in the room (try two tabs)
* Messages submit on enter keypress (and clear the input)
* All connected clients see new messages immediately
* The background flashes yellow when something is loading

Why does each connected client receive realtime updates?

* the server global state atom is shared by all clients connected to that server, because they share the same JVM.
* each client get's its own "electric process", bound to the websocket session
* `(e/def msgs ...)` is global, therefore shared across all sessions (same JVM)
* other than shared global state, the server instances are independent. All sesison state is isolated and bound to the websocket session, just as HTTP request handlers are bound to a request.

regular web server

* Electric's websocket can be hosted by any ordinary Clojure web server, you provide this.
* App server for this app: `[server_httpkit.clj]()` or `[server_jetty.clj]()` (todo - it's in the starter kit)
  * it hosts a websocket
  * it has a `/auth` endpoint configured with HTTP Basic Auth, specifically for this tutorial.
* Note the server is application code, it's not provided by the Electric library dependency.
* Why is the server not included by Electric? Because it has hardcoded http routes and auth examples. This is userland concern.
* You should start with the starter app and modify it.

auth is same as any other web app

* you have access to the request, cookies, etc
* `e/http-request` is the ring request that established the websocket connection.
* it is injected in the `[process entrypoint]()` (todo) where it is passed by server argument to your `[electric app root]()` (todo), where your app entrypoint binds it into dynamic scope.
* Sorry, it's not automatic anymore, dependency injection is a userland concern.
* Use this pattern to inject any additional dependencies from Clojure (or just use a global, or just instantiate resources in your Electric program.)

`InputCreateSubmit?!`
* Read the docstring, study the implentation
* It uses the dom/On-all concurrent tokens API to support submitting a rapid sequence of messages, clearing the input after each submit, without waiting for each individual message to complete.
* Note there's no error handling / retry state, which is why this implementation is "dubious" and we marked it as `?!`. It's fine to use if you don't care about error handling. (What does Slack even do if a message fails? Apple's iMessage handles this correctly, affording a retry state on the optimistic list entry.)
* `dom/node` (used in the Input impl): the live dom node, maintained in dynamic scope for local point writes
* JavaScript interop (via ClojureScript): everything works as expected, direct DOM manipulation is no problem, and idiomatic

Edits - `(e/for [[t msg] edits] ...)`
* `[t v]` (token, value) – this is an important idiom we will use often for server RPC, encoding into this simple token value the lifecycle of a transaction:
  1. an edit has been requested and an input is dirty;
  2. the ability to mark the edit as accepted `(t)`
  3. or mark it rejected via the second arity `(t err)` which sends the error back to the place that issued the edit
* Note, `edits` as a value represents N concurrent in-flight edits, with fine grained reactivity on each individual edit value, for correct lifecycle tracking on each edit. When the edit's token is spent, that edit collapses out of the reactive collection without disturbing the others.

"Pending" aka "Busy" state

* When you send a message and one or more server effects are pending, the input flashes yellow
* Pending is no longer modeled as an exception as it was in v2, for now we are managing this state in userland very specifically.
* two kinds of latency in Electric - query side and command side
* Here, we are interested only in the command side. We want busy state when messages are sent to the server, but NOT during page load.
* `InputSubmitCreate?!` `L49` automatically sets the `{:aria-busy true}` attribute on the input, which is turned yellow with userland css e.g. `L96`
* So the style's extent is the duration of the server edits. Once all edits are accepted, the busy style is cleared.



Pending



`(e/with-cycle* first [edits (e/amb)] ...)`
* note the `*` and the `first`, this is not `e/with-cycle`
* this is a temporary hack
* we need a cycle primitive that cycles tables, not just clojure values

Optimistic updates
* `!` inputs return requested next state as an `edit` i.e. `[t v]`
* Loop the edits locally
* edits fed into query
* query interprets edits, merging them into the previous collection value
* Edit eventually succeeds, txn is cleared `(t)`, pending state falls out of query
* Nice!

Let's formalize this pattern!