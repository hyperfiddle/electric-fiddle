# Two Clocks <span id="title-extra"><span>

<div id="nav"></div>

* The easiest way to understand Electric is **streaming lexical scope**.
* Here, the server clock is streamed to the client.

!ns[electric-tutorial.two-clocks/TwoClocks]()

What's happening

* Two clocks, one on the client, one on the server, we compute the skew.
* The server clock streams to the client over websocket.
* The expression is **multi-tier** (i.e., full-stack) – it has frontend parts and backend parts.
* The Electric compiler infers the backend/frontend boundary and generates the full-stack app (concurrent client and server processes that coordinate).
* **Network "data sync"† is automatic and invisible.** (†I do not like the framing "data sync" because it implies *synchronization*, i.e. the reconciliation of two separate mutable stores which have conflicting views as to what is true. This is not how Electric works! More on that later.)
* When a clock updates, the **reactive** view incrementally recomputes to stay consistent, keeping the DOM in sync with the clocks. Both the frontend and backend parts of the function are reactive.
