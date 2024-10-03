# Chat

* A multiplayer chat app with auth and presence, all in one file.
* When multiple sessions are connected, you can see who else is present.
* Try two tabs

!fiddle-ns[](electric-tutorial.chat/Chat)

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
* App server for this app: [server_httpkit.clj](https://github.com/hyperfiddle/electric-fiddle/blob/main/src-contrib/electric_fiddle/server_httpkit.clj) or [server_jetty.clj](https://github.com/hyperfiddle/electric-fiddle/blob/main/src-contrib/electric_fiddle/server_jetty.clj)
  * it hosts a websocket
  * it has a `/auth` endpoint configured with HTTP Basic Auth, specifically for this tutorial.
* Note the server is application code, it's not provided by the Electric library dependency.
* Why is the server not included by Electric? Because it has hardcoded http routes and auth examples. This is userland concern.
* You should start with the starter app and modify it.

auth is same as any other web app

* you have access to the request, cookies, etc
* `e/http-request` is the ring request that established the websocket connection.
* it is injected in the [process entrypoint](https://github.com/hyperfiddle/electric-fiddle/blob/37fb9cdb2d35ba6a3dc5afcd85c697354c808c1a/src-dev/dev.cljc#L47) where it is passed by server argument to your [electric app root](https://github.com/hyperfiddle/electric-fiddle/blob/37fb9cdb2d35ba6a3dc5afcd85c697354c808c1a/src-contrib/electric_fiddle/main.cljc#L16), where your app entrypoint binds it into dynamic scope.
* Sorry, it's not automatic anymore, dependency injection is userland concern.
* Use this pattern to inject any additional dependencies from Clojure (or just use a global, or just instantiate resources in your Electric program.)

`InputCreateSubmit?!`
* `dom/node` (used in the Input impl): the live dom node, maintained in dynamic scope for local point writes
* JavaScript interop (via ClojureScript): everything works as expected, direct DOM manipulation is no problem, and idiomatic

Edits

* [t v] - this is an important idiom we will use when we build forms

"Pending" aka "Busy" state

* When you send a message and the server effect is pending, the page flashes yellow
* Pending is no longer modeled as an exception as it was in v2, for now we are managing this state in userland very specifically.
* two kinds of latency in Electric - query side and command side
* Here, we are interested only in the command side. We want busy state when messages are sent to the server, but NOT during page load.
* L57 mounts the busy style
* The style is mounted when it's parent e/fn is mounted, and the style is unmounted when the e/fn is unmounted.
* So the style's extent is the duration of the callback, and the callback is the duration of the Pending exception plus one tick for the final result. Once the result is known, the callback (and the style) are unmounted and removed from the DAG.

Query side latency

* `e/client` and `e/server` return reactive values across sites. When a remote value is accessed but not yet available, Electric v3 returns `(e/amb)` until a value is available, essentially parking the computation.
* `e/amb` is discussed in future tutorial

# Scratch

* **30 LOC**: where is all the client/server framework boilerplate? No GraphQL, no fetch, no API modeling, no async types, etc. One thing missing in this tutorial is error handling (resilient state sync, i.e. optimistic updates with rollback). Electric bundles UI controls for this out of the box, to be discussed in a future tutorial.
* (btw, v2's `e/def` has been removed for internal reasons, you don't actually need it.)