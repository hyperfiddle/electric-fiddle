
### dataflow recursion

* above example used a cycle by side effect (like reagent)
* so like, that's a loop actually, does the side effect matter?
* can we model that loop with recursion? Because that would be pure functional!

!fn[](electric-tutorial.forms-from-scratch/DemoInputCircuit2)

* alas, it's an infinite loop.
* We could totally make this work, but there is a syntax collision:
* Do you want N inputs or do you want to loop over the same input?
* Haskell has an extension that matches our problem, "recursive do" which is applicable here. We haven't attempted to implement it yet.
* While we think about how best to express this, we decided to provide a cycle operator in the meantime:

### `e/with-cycle`

!fn-src[hyperfiddle.electric3/with-cycle]()

* this is syntax over the underlying atom impl, which does exactly the right thing!
* Now we can express dataflow cycles directly, without using any icky side effects, and think about the pattern as pure (because it is pure, recall that haskell has it too)

!fn[](electric-tutorial.forms-from-scratch/DemoInputCircuit3)

### `Input` implementation

We can now understand this, it's simple:

!fn-src[hyperfiddle.input-zoo0/Input]()

* it's a controlled input, meaning external state updates (passed in as an argument) will overwrite local state. (Which is why the states are looped - the most recent local state will synchronously loop back in, and the computation reaches a fixed point and halts.)
* why track focused state? To prevent external state updates from overwriting local input state while the user is typing
* why the cycle? This allows usage like `(Input "")` that does not "complete the circuit" – we complete the circuit internally so that when the user blurs the input, `(set! (.-value dom/node) v)` does not revert to the previous value and lose the fresh state. When a new external value eventually comes, `v` will update and `(set! (.-value dom/node) v)` will track it.

!fn-src[hyperfiddle.electric-dom3/Focused?]()

* `squarewave` uses missionary to merge a pair of events (rising edge and falling edge) into a square wave (`true` and `false`) with an initial state.

### `Input!` implementation

e.g. `(Input! :user/str1 x)`

!fn-src[hyperfiddle.input-zoo0/Input!]()

* `field-name` argument e.g. `:user/str1` - the *name* of the input in the form. Like the DOM, `<input name=...>`
  * Why? Dirty state! How can you know which fields in a form have been touched? You need not a *form value* but rather a *set of dirty fields*!
* `e/RetryToken` - like `e/Token`, but the second arity `(t ::rejected)` lets the service inject an error, which means `[t err]` becomes `[nil ::rejected]`.
  * (backlog - we should replace all `Token` calls with `RetryToken` right?)
* internal state machine
  * `editing?`
  * `waiting?` - user interaction has changed the value, a token is allocated, it is now being bubbled up to the service, which has not yet invoked the token to inform us the result (success or failure).
  * `error?`
  * `dirty?`
* `(if waiting? [t (edit-monoid k v)] (e/amb)`
  * `edit`, e.g. `(edit-monoid k v)` (defaults to `hash-map`) - an *edit* - a KV structure that contains the field name and its target value. This edit type must be a monoid because the form will use it to merge a set of field edits into a single batch form edit for atomic commit.
  * when `waiting?`, return the *edit request* (token + edit) to the service
  * returns nothing (`(e/amb`, NOT `nil`) when not `waiting?`. This prevents spurious `nil` edit requests, and also enables requesting concurrent independent edits, such as `(e/amb (Input! :foo ...) (Input! :bar ...))`




      * This is a Clojure function (not Electric function) to bypass Electric's reactive work skipping semantics, for two reasons:
    * 1) `.-target` is a reference not a value meaning `.-value` will be workskipped incorrectly.
    * 2) you sometimes need to run side effects like `(.preventDefault e)` on every event, and Electric's continuous time semantics may skip effects if the system's sampling rate is slower than the event rate.


      * `""` is the signal's initial state
  * `#(-> % .-target .-value)` is a discrete sampler function that derives the current state from the event.




  ### `e/with-cycle`

!fn[](electric-tutorial.forms-from-scratch/DemoInputCircuit-amb)

* Equivalent - "cycle by side effect", looping via reactive atom
* in fact, that's how `e/with-cycle` is implemented today
* but, the idiom is still pure functional, the fact that we happen to use an imperative implementation of the cycle operator does not mean the operator is impure. Electric could reify dataflow cycles with a special form just as Haskell does, and maybe we will some day.


Note: using `e/with-cycle` here is confusing, because really we want to loop the table (not force the table to collapse into a singular value), so let's not do that. (See: optimistic updates, which loops tables)


### `e/with-cycle` – dataflow recursion

This "cycle by atom side effect" pattern is so common that we provide `e/with-cycle`, a macro over it:

!fn[](electric-tutorial.forms-from-scratch/DemoFormSync-cycle)

* Think of this as an electrical circuit, it's a closed loop!
* Squint and you can almost fit it to loop/recur:
  * `(loop [s ""] (recur (Input s)))`
  * (it doesn't quite match, e.g. we want 1 input not N - so we need a new primitive)
* I point this out because, loop/recur is pure functional. It is just recursion. Recursion is not a side effect.
* Dataflow cycles are also pure functional. In Haskell see the [RecursiveDo](https://ghc.gitlab.haskell.org/ghc/doc/users_guide/exts/recursive_do.html) extension which introduces a `rec` keyword that matches this structure.