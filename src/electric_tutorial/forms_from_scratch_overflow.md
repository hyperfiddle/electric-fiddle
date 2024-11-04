
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