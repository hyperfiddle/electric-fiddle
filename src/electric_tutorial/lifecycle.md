# Lifecycle

mount/unmount component lifecycle

!fiddle-ns[](electric-tutorial.lifecycle/Lifecycle)

What's happening

* The string "blink!" is being mounted/unmounted every 2 seconds
* The mount/unmount "component lifecycle" is logged to the browser console with `println`
* `(BlinkerComponent)` is being constructed and destructed like an object!
* Diffs are printed to console, let's take a look

Electric functions have object lifecycle
* Reactive expressions have a "mount" and "unmount" lifecycle. `println` here runs on "mount" and never again since it has only constant arguments, unless the component is destroyed and recreated.
* `e/on-unmount` : takes a regular (non-reactive) function to run before unmount.
* Why no `e/mount`? The `println` here runs on mount without extra syntax needed, we'd like to see a concrete use case not covered by this.
* **Electric fns are both functions and objects**: They compose as functions, they have object lifecycle, and they have state. From here on we will refer to Electric fns as both "function" or "object" as appropriate, depending on which aspects are under discussion. We also sometimes refer to "calling" Electric fns as "booting" or "mounting".
* **DAG as a value**: Electric lambdas `e/fn` are values, these can be thought of as "higher order DAGs", "DAG values" or "pieces of DAG".

Reactive control flow

* `if`, `case` and other Clojure control flow forms are reactive. Here, when `x` toggles, `(case x)` will *switch* between branches. In the DAG, if-nodes look like a railroad switch:
  <p>![railroad switch](https://clojureverse.org/uploads/default/original/2X/7/7b52e4535db802fb51a368bae4461829e7c0bfe5.jpeg)</p>

Dynamic extent

- Electric objects have *dynamic extent*.
- "Dynamic extent refers to things that exist for a fixed period of time and are explicitly “destroyed” at the end of that period, usually when control returns to the code that created the thing." — from [On the Perils of Dynamic Scope (Sierra 2013)](https://stuartsierra.com/2013/03/29/perils-of-dynamic-scope)
- Like [RAII](https://en.wikipedia.org/wiki/Resource_acquisition_is_initialization), this lifecycle is deterministic and intended for performing resource management effects.

Process supervision

- Electric `if` and other control flow nodes will mount and unmount their child branches (like switching the railroad track).
- If an `e/fn` were to be booted inside of an `if`, the lifetime of the booted lambda is the duration for which the branch of the `if` is active.
- Electric objects can manage references (e.g. DOM node or atom in lexical scope).
- A managed reference's lifetime is tied to the supervising object's lifetime.

Object state

- Recall that Electric functions are auto-memoized. This memo buffer can be seen as the *object state*.
- The memo buffer is discarded and reset when this happens.
- In other words: Electric flows are not [*history sensitive*](https://blog.janestreet.com/breaking-down-frp/). (I hesitate to link to this article from 2014 because it contains confusion/FUD around the importance of continuous time, but the coverage of history sensitivity is good.)

# Scratch


* `do` sequences effects, returning the final result (same as Clojure). Reactive objects in the body are constructed in order and then run concurrently, so e.g. `(do (Blinker.) (Blinker.))` will have concurrent blinkers.