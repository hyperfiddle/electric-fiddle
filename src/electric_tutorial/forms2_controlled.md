# Controlled forms

* `Input` is your bread and butter input, good for ephemeral client local state, such as search or filtering or implementing Typeaheads and other composite controls.

!fiddle-ns[](electric-tutorial.forms2-controlled/Forms2-controlled)

What's happening
* Two forms this time, now with initial form state (L6 L24), and both forms are bound to the same state (i.e., "controlled")
* When you interact with one form, both update immediately.
* the aggregate form state is printed
* there is no server, everything is local, therefore also no error handling
* doesn't lose state in presence of conflicts (as demonstrated in the <a href="http://localhost:8080/(electric-tutorial.tutorial!%54utorial)/(electric-tutorial.temperature!%54emperature)">Temperature tutorial</a>)
* **`UserForm` is a pure function**, it takes its current state as input and returns its current state as output, like an electrical circuit!

Why / what for

* you always want latest, there are never conflicts, so there's no failure/retry. safe for concurrent writes (see temperature tutorial)
* synchronous, local, always succeeds, always ready, no error states. No dirty state! Becuse, since the edits are guaranteed to succeed, there is no value to that state!
* Sync has nothing to do with it - it can be async - if you have a data layer that guarantees success, that would work too. BUT - even if guaranteed to succeed, eventually, it implies - what if there is a network partition and it fails or timesout and you need to retry? That is not what this input is about.

* Tutorial usage: <a href="http://localhost:8080/(electric-tutorial.tutorial!%54utorial)/(electric-tutorial.temperature!%54emperature)">Temperature</a>, <a href="http://localhost:8080/(electric-tutorial.tutorial!%54utorial)/(electric-tutorial.fizzbuzz!%46izz%42uzz)">Fizzbuzz</a>, Typeahead? (todo)

How it works

* `Input` models a simple dataflow circuit (i.e., "controlled" state). This interface is pure functional!
* State is handled not with atoms but with a **cycle** in the DAG†. (So if there are cycles is it not a DAG? Well kinda, technically it's still a DAG, we will explain)
* recall `e/with-cycle` from the Temperature Converter tutorial
* Actually let's switch to a simpler imperative implementation (exactly equivalent), which will have familiar control flow:

!fn-src[electric-tutorial.forms2-controlled/Forms2-controlled']()

* `(e/amb (UserForm m) (UserForm m))` is used to render the form twice.
* each form returns its state as a map, so the `e/amb` returns the superposition of the two form states.

differential `e/amb` (this is important)

* Note that `(reset! !m (e/amb form1 form2))` is a differential product. What's happening? Why does it work?
* Recall that e/amb superposition is *reactive* and *differential* – that means, only *changes* propagate.
* When the top form state changes, the e/amb will collect the values (or from the differential perspective – the *changes*), and propagate that new value to the `e/with-cycle`.
* `(e/with-cycle [m state0] (e/amb (UserForm m) (UserForm m)))` will loop that top form's updated state back into *both* forms. The top form receives it's *own* changed state, which is the same as the current state so it reaches a fixed point and halts.
* The bottom form receives the changed state, which is NOT the same as the current state, so it absorbs the new state, and then closes the circuit by emitting the new state, which propagates into the `e/with-cycle`, which is now a fixed point so the computation halts.
* This is a pure functional pattern!

* with-cycle will loop until the input reaches a fixed point, i.e. `m == (UserForm m)` — the value passed into the form is the same as the value it returns.
* Of course, this is a multiplexed form with e/amb, so ...

Mastery of the above (differential e/amb) is necessary in order to fully grok the remaining forms patterns, which are quite sophisticated.

`Input` implementation

!fn-src[hyperfiddle.input-zoo0/Input]()

* why internal cycle
* focused - prioritize uncommitted dirty state over new/conflicting values from controlled state
* why emit immediately and complete the circuit
  * consequences - no server transactions or side effects - you'll slam the database with each keystroke!