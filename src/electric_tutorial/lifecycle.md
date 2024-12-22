# Lifecycle <span id="title-extra"><span>

<div id="nav"></div>

mount/unmount component lifecycle

!ns[electric-tutorial.lifecycle/Lifecycle]()

What's happening

* The string "blink!" is being mounted/unmounted every 2 seconds
* The mount/unmount "component lifecycle" is logged to the browser console with `println`
* `(BlinkerComponent)` is being constructed and destructed like an object!
* Diffs are printed to console, let's take a look

Reactive control flow

* `if`, `case` and other Clojure control flow forms are reactive.
* Here, when the predicate switches, the `when` will *switch* between branches.
* the hidden branch is `nil`, because this is actually `clojure.core/when`, which is a macro over `if`, which Electric reinterprets as reactive.
* In the DAG, if-nodes look like a railroad switch:
  <p>![railroad switch](https://clojureverse.org/uploads/default/original/2X/7/7b52e4535db802fb51a368bae4461829e7c0bfe5.jpeg)</p>

Electric functions have object lifecycle
* Reactive expressions have a "mount" and "unmount" lifecycle. `println` here runs on "mount" and never again since it has only constant arguments, unless the component is destroyed and recreated.
* `e/on-unmount` : takes a regular (non-reactive) function to run before unmount.
* Why no `e/mount`? The `println` here runs on mount without extra syntax needed, we'd like to see a concrete use case not covered by this.

**Electric fns are both functions and objects**
* Recall that Electric exprs are auto-memoized.
* That's a white lie - in fact, it is electric `let` which allocates the memo buffer, because that's the point at which sharing and reuse becomes possible.
* These memo buffers can be seen as *object state*. Electric functions, that contain a let node, are in fact objects.
* They compose as functions, they have object lifecycle, and they have state. From here on we will refer to Electric fns as both "function" or "object" as appropriate, depending on which aspects are under discussion. We also sometimes refer to "calling" Electric fns as "booting" or "mounting".
* Electric `if` and other control flow nodes will mount and unmount their child branches (like switching the railroad track).
* When Electric objects are eventually destroyed, the memo buffers are discarded.
  * In other words: Electric flows are not [*history sensitive*](https://blog.janestreet.com/breaking-down-frp/). If you unmount and remount an Electric function, the past state is gone. (I hesitate to link to this article from 2014 because it contains confusion/FUD around the importance of continuous time, but the coverage of history sensitivity is good.)

Dynamic extent, RAII and process

* Electric objects can manage references (e.g. DOM node or atom in lexical scope).
* A managed reference's lifetime is tied to the supervising object's lifetime.
* Electric objects have *dynamic extent*:
* "Dynamic extent refers to things that exist for a fixed period of time and are explicitly “destroyed” at the end of that period, usually when control returns to the code that created the thing." — from [On the Perils of Dynamic Scope (Sierra 2013)](https://stuartsierra.com/2013/03/29/perils-of-dynamic-scope)
* Like [RAII](https://en.wikipedia.org/wiki/Resource_acquisition_is_initialization), this lifecycle is deterministic and intended for performing resource management effects.

`do` sequences effects, returning the final result (same as Clojure)
* Reactive objects in the body are constructed in order and then run concurrently
* so e.g. `(do (Blinker.) (Blinker.))` will boot two concurrent blinkers