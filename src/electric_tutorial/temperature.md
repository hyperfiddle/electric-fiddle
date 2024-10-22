# Local input — Temperature Converter

* `Input` is your bread and butter input, good for ephemeral client local state
* introduce cycle-by-side effect, and begin to understand dataflow cycles as a pure functional encoding of state

!fiddle-ns[](electric-tutorial.temperature/Temperature)

What's happening
* two form inputs recursively bound (typing in 1 updates the other)
* there's a writer process concurrently setting the form to random values, even as you type.
* the inputs are stable and do not lose state desite the concurrent writes
* blur works - try tab and shift-tab to switch focus between inputs

`Input` is simple controlled input. It's callback-free!

* `Input` returns it's current state as a reactive value (i.e., returns its current state as a *signal* from the FRP perspective – an async sequence of values over time, e.g. electrical voltage)
* Since the input returns its state as a value, you can compose functions with it directly, e.g. `parse-long`!
* I like to thread my event processing code because it lets the view read left to right, and it is reminiscent of the callback it replaces.
* `(some->> ... not-empty)` filters out `""`, the underlying dom input is stringly typed and will happily return `""` even with `:type "number"`.
* This is by design: we are not interested in fixing the DOM, we are interested in pure functional state management that is transparent with respect to platform semantics to the best of our ability.

Concurrent writer process

* `e/input` - join the foreign missionary flow with the electric flow
* Electric's continuous time semantics (signal not stream) require that the foreign flow have an initial value, here `0`.
* note that `m/ap` is a value, it does not run until it is joined.

Recursive bindings, i.e. two controlled inputs with a mutual dependency

* We use an `atom` to loop input field state changes back to the top to feed into the other input.
* there's an `atom` and a `watch`. Recall that if you change the atom, the watch will fire.
* Interact with the upper input. The input returns its updated state.
* `(reset! !v ...)` is called again with the next state, notifying the `e/watch` through the `clojure.core/add-watch` subscription machinery
* `(e/watch !v)` is now dirty, `v` will propagate forward to both inputs (also the prn)
* both inputs will "close the circuit":
  * the first input—which started this propagation turn in the first place—will receive the value and return it again, "closing the circuit". This computation has now reached a **fixed point** - the Input returns the same value again, and will halt at, um, the `not-empty`, per reactive semantics.
  * Concurrently, the second input receives the new value, writes the new state to the DOM, and returns the new value, propagates it through the `some->>` all the way to `(reset! !v ...)` which will run (the new value is different than the last time this expression computed), touching the atom (to the same value) and notifying the e/watch, which will now itself reach a fixed point, returning the same value twice in a row.

This is called **"cycle by side effect"**

* Thus mutable state in a reactive lang can be used to implement a loop: `(reset! !m (f (e/watch !m)))`
* the `reset!` is an *output* of the DAG, the side effect is out of band!
* then, this output is fed back into the DAG as an input, using `e/watch` which subscribes to the event
* the atom will synchronously update with the `reset!` (i.e., reset! calls into clojure, and clojure calls into the atom machinery, which notifies all watches, all *synchronously* before returning control to Electric)

DAG = Directed Acyclic Graph. But this is a cycle?

* Indeed, Electric programs can and do contain cycles, as we have just demonstrated.
* So, yes, UIs are totally "signal circuits" and not really "streaming DAGs" as I claimed in the 2022 talk. Whoops!
* Nonetheless, as of yet the Electric implementation does not *reify* cycles. Electric's implementation is, technically, acyclic, i.e. a DAG.
* Cycle by side effect means Electric is not aware of the loop. Clojure side effects can do anything! This side effect happens to loop, but it's Clojure's semantics causing it, not Electric's.
* Future work: should Electric reify cycles? Probably yes! Does it matter? Don't know yet.
* In the meantime, you can and should use this technique to encode cycles with mutable state!

Cycles are so common that we provide `e/with-cycle`. On to the next tutorial!

7 GUIs reference: <https://eugenkiss.github.io/7guis/tasks#temp>