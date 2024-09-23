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

Recursive binding - the inputs are looped
* DAG = Directed Acyclic Graph. But what about cycles?
* Indeed, Electric programs can reasonably contain cycles, as we will see in a moment in the temperature converter tutorial. So, yes, UIs are totally "signal circuits" and not really "streaming DAGs" as I claimed in the 2022 talk. Whoops!
* Nonetheless, as of yet the Electric implementation does not *reify* cycles. You can and should encode cycles out of band with mutable state.

* `rec` in Haskell
* so this is, like, conceptually pure
* `e/with-cycle`?

Concurrent writer process
* `e/Task`
* missionary task - it never returns

7 GUIs reference: <https://eugenkiss.github.io/7guis/tasks#temp>