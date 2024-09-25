# Temperature Converter

Two controlled inputs with a mutual dependency. There's also a background process generating concurrent writes.

!fiddle-ns[](electric-tutorial.temperature/Temperature)

What's happening
* two form inputs recursively bound (typing in 1 updates the other)
* there's a writer process randomly setting the form
* the inputs are stable and do not lose state desite the concurrent writes
* blur works - try tab and shift-tab to switch focus between inputs

!fn-src[hyperfiddle.input-zoo0/Input]()

Callback-free input
* `Input` - a simple controlled input - numeric only
* `dom/On`
* it's controlled, so we need to deal with focused to not clobber on concurrent edit conflict
* dom/on emits intial state - circuit pattern - this allows atom-like usage, like (Input "fizz")
* this input requires you to loop the new value, otherwise it will lose it on focus!
* We'll talk a lot more about inputs in the crud forms tutorials - coming soon
* Stable through concurrent edits
* What kind of input is this?
* Since the state is local (no latency, no transaction) we drop to the lower signal API with no token.
* Why guard focused state? Concurrent writes should not clobber the user's dirty state as they type
* Why coerce to string? For consistency, so we can use the same input abstractions for any parsed type by putting all parsing code in userland.
* props


* note `parse-long` on L15, I like to thread my event processing code because it feels like a callback and lets the view read left to right,

Recursive binding - the inputs are looped
* DAG = Directed Acyclic Graph. But what about cycles?
* Indeed, Electric programs can reasonably contain cycles, as we will see in a moment in the temperature converter tutorial. So, yes, UIs are totally "signal circuits" and not really "streaming DAGs" as I claimed in the 2022 talk. Whoops!
* Nonetheless, as of yet the Electric implementation does not *reify* cycles. You can and should encode cycles out of band with mutable state.


* there's an `atom` and a `watch`. Recall that if you change the atom, the watch will fire.
* Interact with the upper `(UserForm m)`. An updated state is returned.
* Let's ignore the e/amb and come back to that
* `(reset! !m ...)` is called again with the next state
* `e/watch` is now dirty, `m` will propagate forward into both `(UserForm m)`
* which will return `m`, which will call `(reset! !m m)`, which will trigger `(e/watch !m)`, ... it's a loop!
* Q: When does the loop stop? A: Electric's reactive semantics only propagate forward changed values (i.e. `(reset! !x m)` is only called when `(not= m m')`, it is HALTED when `(= m m')`.

This is called **"cycle by side effect"**
* Thus mutable state in a reactive lang can be used to implement a loop: `(reset! !m (e/watch !m))`
* the `reset!` is an *output* of the DAG, the side effect is out of band!
* the atom will synchronously update with the `reset!` (i.e., reset! calls into clojure, and clojure calls into the atom machinery, which notifies all watches, all *synchronously* before returning control to Electric)

And in fact, this is how `e/with-cycle` is actually implemented:

!fn-src[hyperfiddle.electric3/with-cycle]()

Dataflow cycle = state
* This is a pure functional pattern!

Electric is technically acyclic
* Cycle by side effect means Electric does not realize that there is a side effect there. Clojure side effects can do anything! This side effect happens to loop, but it's Clojure's semantics causing it, not Electric's.
* That's why Electric's computation model is still technically acyclic, i.e. DAG.
* Future work: should Electric reify cycles? Probably yes! Does it matter? Don't know yet


* `rec` in Haskell
* so this is, like, conceptually pure
* `e/with-cycle`?

Concurrent writer process
* `e/Task`
* missionary task - it never returns

7 GUIs reference: <https://eugenkiss.github.io/7guis/tasks#temp>