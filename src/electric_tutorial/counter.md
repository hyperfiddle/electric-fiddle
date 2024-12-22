# Counter <span id="title-extra"><span>

<div id="nav"></div>

* Demonstrates Electric lambda's powerful upgrades in v3, as well as concurrent event processing in the presence of latency.
* There is progress, and concurrency. Try mashing the button.
* It's multiplayer – the counter is on the server.

!ns[electric-tutorial.counter/Counter]()

What's happening

* counter on server
* progress indicator - counting down from 10 and then terminating
* concurrent event processing - mash the button!

Concurrent event processing with `dom/OnAll`
* x

Reactive lambda `e/fn`
* supports client/server transfer

Distributed lambda

* the button callback spans both client and server. As does the `Toggle` function itself.

Serializable lambda
* the lambda "value" is serializable
* it started on the server and then moved to client
* check the console for `{1 [:electric-tutorial.counter/Counter 1 [] {}]}`
* it's not actually moving the lambda, both sites see the structure of the lambda at compile time. We're just moving the pointer and both sites understand the pointer.

Server RPC processing

* `e/Offload` - run a blocking computation (thunk) to a threadpool. (Electric is an async computation, please do not block, if you do, it will block all concurrently connected sessions i.e. your other tabs!)

Progress

* `e/snapshot` - snapshot the current state of a reactive value, severing reactivity
* `(- (e/snapshot (e/System-time-ms)) (e/System-time-ms))` captures the time of the click, and subtract it from the running time, in order to get a countdown
* the progress timer is returned from the callback and given back to the button
* this is a contorted pattern (why are N counters rendering to one button? That's never what you want). A better pattern is to extend the optimistic collection patterns in upcoming tutorials with progress functionality through the token interface (future work)

Open questions

```clojure
(e/for [[t x] (Button! ::inc :label "inc")]
  (case x ::inc (F! t))) ; how to use token interface to feed back progress?
```

7 GUIs reference: <https://eugenkiss.github.io/7guis/tasks/#counter>
