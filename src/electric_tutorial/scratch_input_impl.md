# `Input` impl

* `Input` models a simple dataflow circuit (i.e., "controlled" state). This interface is pure functional!

!fn-src[hyperfiddle.input-zoo0/Input]()

* dom/on emits intial state - modeling a circuit, the current state is always available to be read - like an electric circuit voltage
* Input is looping `v` internally, which is nice because the callsite might forget to loop the value back in. This also enables atom-like usage like `(Input "fizz")` (compare to `(atom "fizz")`) where we initialize the input, read changes, and don't want to bother with the loop.
* Why guard the focused state? Concurrent writes (like this tutorial) should not clobber the user's dirty state as they type
* Why coerce to string? For consistency with how the DOM works, and also so we can use the same input abstractions for any parsed type by putting all parsing code in userland.
* why internal cycle
* focused - prioritize uncommitted dirty state over new/conflicting values from controlled state
* why emit immediately and complete the circuit
  * consequences - no server transactions or side effects - you'll slam the database with each keystroke!
* We'll talk a lot more about inputs in the Form tutorials - coming soon