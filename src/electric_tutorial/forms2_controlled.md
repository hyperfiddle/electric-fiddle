# Local input (controlled state, synchronous, ephemeral)

* `Input` is your bread and butter input, good for ephemeral client local state
* Not network-safe

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

What's NOT happening
* There is no HTML form, therefore there is **no form submit semanatic!**
* no server (i.e., intended for local use only†). Therefore, no failure handling!
* no `commit` intent as edits are synchronous and must succeed immediately (i.e., `(reset! !m ...)` must not fail).
* no `dirty` state nor `discard` intent as there is no internal buffer and no window of opportunity to discard
* no `busy` state, no cancellation of inflight commits, control is always `ready` for new input
* no `failure` state, no `retry` intent

Why / what for

* Search, filtering or implementing Typeaheads and other composite controls.
* Tutorial usage: <a href="http://localhost:8080/(electric-tutorial.tutorial!%54utorial)/(electric-tutorial.temperature!%54emperature)">Temperature</a>, <a href="http://localhost:8080/(electric-tutorial.tutorial!%54utorial)/(electric-tutorial.fizzbuzz!%46izz%42uzz)">Fizzbuzz</a>, Typeahead? (todo)

†Why no network?

* Techncially, you can use `e/server` to round trip form state through network, but you should not do this because a network timeout, partition or server failure will violate the assumptions of the pattern.
* Even in a conflict-free (i.e. CRDT) persistence strategy, the user wants to know if their data entry has been saved. PKM tools that lose data due to backend bugs is one clear example that no remote state is error free and virtually no data layer can guarantee success.

How it works
* `(e/amb (UserForm m) (UserForm m))` is used to render the form twice and collect their inputs in superposition

!fn-src[electric-tutorial.forms2-controlled/Forms2-controlled]()

* each form returns its state as a map, so the `e/amb` returns the superposition of the two form states.
* When one of the states change, we feed it into the cycle, which loops it into both forms (i.e., the other form).

Here is the equivalent imperative implementation, which is revealing:

!fn-src[electric-tutorial.forms2-controlled/Forms2-controlled']()

* `(reset! !m (e/amb a b))` implies *auto-mapping*: the `reset!` will run when *either* form changes!
* so if you change `a`, you'll get `(reset! !m a)`, which then loops `a` into `b` ...
* `b` will "close the circuit" by returning the same value ...
* at which point `(reset! !m b)` will *work skip* because `(= a b)`! THe computation has reached a fixed point and the loop halts.
* I suppose you could write this with `e/for` if you want:
  * `(e/for [m (e/amb a b)] (reset! !m m))`

FAQ: What if I want "uncontrolled" inputs?

* This control can express uncontrolled forms by not looping the input, i.e. `(Input "")`.
  * The control will initialize to `""`, but as the control state evolves, the `""` will never change (i.e. never updates again) and therefore can never override the internal state per reactive propagations emantics.
  * This is a departure from the React.js controlled input semantics you may be accustomed to! Electric is only equational if you actually loop the output back into the input, like an electrical circuit.
* See: <a href="/404">form-inline-submit (todo)</a> where we use this pattern to implement the debug settings.