# Temperature Converter

* Two controlled inputs with a mutual dependency.
* There's also a background process generating concurrent writes.
* Goal: begin to understand dataflow cycles as a pure functional encoding of state

!fiddle-ns[](electric-tutorial.temperature/Temperature)

What's happening
* two form inputs recursively bound (typing in 1 updates the other)
* there's a writer process randomly setting the form, as you type
* the inputs are stable and do not lose state desite the concurrent writes
* blur works - try tab and shift-tab to switch focus between inputs

`Input` is simple controlled input. It's callback-free!

* `Input` returns it's current state as a reactive value (i.e., returns its current state as a *signal* from the FRP perspective – an async sequence of values over time, e.g. electrical voltage)
* Since the input returns its state as a value, you can compose functions with it directly, e.g. `parse-long`!
* I like to thread my event processing code because it lets the view read left to right, and it is reminiscent of the callback it replaces.
* `(some->> ... not-empty)` filters out `""`, the underlying dom input is stringly typed and will happily return `""` even with `:type "number"`

Recursive binding - the inputs are looped back to the top so they can be fed back into the other input.

* there's an `atom` and a `watch`. Recall that if you change the atom, the watch will fire.
* Interact with the upper input. The inpput returns its updated state.
* `(reset! !v ...)` is called again with the next state, notifying the `e/watch` through the `clojure.core/add-watch` subscription machinery
* `(e/watch !v)` is now dirty, `v` will propagate forward to both inputs (also the prn)
* both inputs will "close the circuit":
  * the first input—which started this propagation turn in the first place—will receive the value and return it again, "closing the circuit". This computation has now reached a **fixed point** - the Input returns the same value again, and will halt at, um, the `not-empty`, per reactive semantics.
  * Concurrently, the second input receives the new value, writes the new state to the DOM, and returns the new value, propagates it through the `some->>` all the way to `(reset! !v ...)` which will run (the new value is different than the last time this expression computed), touching the atom (to the same value) and notifying the e/watch, which will now itself reach a fixed point, returning the same value twice in a row.

This is called **"cycle by side effect"**

* Thus mutable state in a reactive lang can be used to implement a loop: `(reset! !m (e/watch !m))`
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

Cycles are so common that we provide `e/with-cycle`:

!fn-src[hyperfiddle.electric3/with-cycle]()

* Dataflow cycle = state; this is a pure functional pattern!
* we could subsitute a pure implementation of with-cycle – for example, possibly using function recursion to implement the loop (we would need something akin to tail recursion)
* See Haskell's [RecursiveDo](https://ghc.gitlab.haskell.org/ghc/doc/users_guide/exts/recursive_do.html)

Now we can look at `Input`'s implementation:

!fn-src[hyperfiddle.input-zoo0/Input]()

* dom/on emits intial state - modeling a circuit, the current state is always available to be read - like an electric circuit voltage
* Input is looping `v` internally, which is nice because the callsite might forget to loop the value back in. This also enables atom-like usage like `(Input "fizz")` (compare to `(atom "fizz")`) where we initialize the input, read changes, and don't want to bother with the loop.
* Why guard the focused state? Concurrent writes (like this tutorial) should not clobber the user's dirty state as they type
* Why coerce to string? For consistency with how the DOM works, and also so we can use the same input abstractions for any parsed type by putting all parsing code in userland.
* We'll talk a lot more about inputs in the Form tutorials - coming soon

Concurrent writer process

* `e/input` - join the foreign missionary flow with the electric flow
* the foreign flow must have an initial value, here 0

7 GUIs reference: <https://eugenkiss.github.io/7guis/tasks#temp>