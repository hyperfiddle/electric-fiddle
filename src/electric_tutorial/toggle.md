# Buttons and events — Toggle

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

`e/Token` explainer

when i first played with the token api i strongly disliked the exposed state machine, but what i realized is that every nontrivial use case actually is a state machine

* The token pattern is the basis for transactional event processing in v3, yes, but we can be more sophisticated than this - we will show you how
* haven't looked at v3 yet but Token is the second coolest thing i've seen coming up. turn any callback into a value, which serves as a signal for lifecycle management? would it be useful if spend had another arity to pass along arbitrary signals?


Also, I dont really like dom/On as an API because it forces you into the mindset of processing one event at a time (and like, disabling the button while we wait). dom/OnAll is the superior API that forces you to acknowledge that events can run concurrently, especially when you want to process them on the server which adds latency. Imagine a chat app where you send messages rapidly without the chatbox being disabled until the message goes through. However, sometimes dom/On is what you need, so it stays.
* events don't run concurrently, they happen instanteously
* their effects might persistent concurrently which is what your handles model
* the event APIs remind me of spans in telemetry (https://opentelemetry.io/docs/languages/ruby/instrumentation/#add-span-events) but with 2 special events instead of generalized


I don't like the sugar for a couple reasons
electric functions are not really callbacks, pretending so caused a bunch of problems in v2, i think their true nature should be apparent, which means the spend effect must be visible
modeling progress requires the programmer to control when spend is called
we broadly intend to encourage callback-free style with patterns for optimistic updates, optimistic list maintenance - it is more powerful
the unsugared version is quite good, it will grow on you


Transaction processing
`case` idiom to sequence imperative statements
* "and then"
* why ::ok is sequenced - because `do` is concurrent! the `::ok` might come first