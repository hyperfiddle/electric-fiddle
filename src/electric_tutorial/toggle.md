# Toggle (buttons and events)

This demo toggles between client and server with a button, to introduce basic event handling idioms, in particular for remote side effects (RPC).

!fiddle-ns[](electric-tutorial.toggle/Toggle)

What's happening
* There's a button on the frontend, with a callback, that toggles a boolean, stored in a server-side atom.
* That boolean is used to switch between a client expr and a server expr.
* Both exprs print the platform number type, which is either a `java.lang.Long` or a javascript `Number`.
* The resulting string is streamed from server to client over network, and written through to the DOM.
* The button is disabled until the event processing completes.

Single state atom
* single state atom UI pattern, except the atom is on the server.
* Clojure/Script interop: The atom definition is ordinary Clojure code, which works because this is an ordinary `.cljc` file.
* `e/watch`: derives a reactive flow from a Clojure atom by watching for changes

Callback-free button
* `dom/button`: a button with managed load state. On click, it becomes disabled until the callback result —possibly remote— is available.
* `e/Token`

Token explainer

Transaction processing
`case` idiom to sequence imperative statements
* "and then"
* why ::ok is sequenced - because `do` is concurrent! the `::ok` might come first