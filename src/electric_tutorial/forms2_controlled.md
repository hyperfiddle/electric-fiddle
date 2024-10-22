# `e/with-cycle` — Forms2-controlled

* introduce `e/with-cycle` as a pure functional encoding of state
* goal: begin to get comfortable with dataflow circuits as a pure functional pattern for encoding state

!fiddle-ns[](electric-tutorial.forms2-controlled/Forms2-controlled)

What's happening

* a form with 3 fields (string/text, long/numeric, bool/checkbox)
* the numeric control has the native inc/dec affordance - hover
* controlled - so there can be initial state
* uncontrolled configuration - pass a constant, i.e. `(Input "")`.
* Two forms this time, now with initial form state (L6 L24), and both forms are bound to the same state (i.e., "controlled")
* When you interact with one form, both update immediately.
* the aggregate form state is printed
* doesn't lose state in presence of conflicts (as demonstrated in the <a href="http://localhost:8080/(electric-tutorial.tutorial!%54utorial)/(electric-tutorial.temperature!%54emperature)">Temperature tutorial</a>)
* **`UserForm` is a pure function**, it takes its current state as input and returns its current state as output, like an electrical circuit!

Why / what for

* Search, filtering or implementing Typeaheads and other composite controls.
* Tutorial usage: <a href="http://localhost:8080/(electric-tutorial.tutorial!%54utorial)/(electric-tutorial.temperature!%54emperature)">Temperature</a>, <a href="http://localhost:8080/(electric-tutorial.tutorial!%54utorial)/(electric-tutorial.fizzbuzz!%46izz%42uzz)">Fizzbuzz</a>, Typeahead? (todo)

†Why no network?

* Techncially, you can use `e/server` to round trip form state through network, but you should not do this because a network timeout, partition or server failure will violate the assumptions of the pattern.
* Even in a conflict-free (i.e. CRDT) persistence strategy, the user wants to know if their data entry has been saved. PKM tools that lose data due to backend bugs is one clear example that no remote state is error free and virtually no data layer can guarantee success.

How it works - start with the imperative impl, which is revealing:

!fn-src[electric-tutorial.forms2-controlled/Forms2-controlled']()

This cycle idiom is so common, we reify it as `e/with-cycle`:

* this reifies the `(reset! !x (f (e/watch !x)))` idiom into a "pure functional" loop operator, reminiscent of Clojure's `loop/recur`.
* In Haskell see [RecursiveDo](https://ghc.gitlab.haskell.org/ghc/doc/users_guide/exts/recursive_do.html) for recursive bindings in certain monadic computations, such as the continuous time dataflow computation used by Electric.

!fn-src[hyperfiddle.electric3/with-cycle]()

* we could subsitute a pure implementation of with-cycle – for example, possibly using function recursion to implement the loop (we would need something akin to tail recursion to ensure we reuse the same reactive "frame" each iteraction, which the atom implementation correctly does out of the box, which is why we do it.)
* Lesson: dataflow cycles are isomorphic to state; this is a pure functional pattern actually! Remember, the reactive engine's implementation is imperative for performance, that does not mean the language operators are impure! Electric's core operators are pure! This operator is pure!

Here's the final Form impl using `e/with-cycle`:

!fn-src[electric-tutorial.forms2-controlled/Forms2-controlled]()

FAQ: What if I want "uncontrolled" inputs?

* This control can express uncontrolled forms by not looping the input, i.e. `(Input "")`.
* The control will initialize to `""`, but as the control state evolves, the `""` will never change (i.e. never updates again) and therefore can never override the internal state per reactive propagations emantics.
* This is a departure from the React.js controlled input semantics you may be accustomed to! Electric is only equational if you actually loop the output back into the input, like an electrical circuit.