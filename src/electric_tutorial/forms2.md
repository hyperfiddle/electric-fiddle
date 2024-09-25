# Input — simple dataflow circuit (i.e., "controlled")



!fiddle-ns[](electric-tutorial.forms2-controlled/Forms2-controlled)

* synchronous, local, always succeeds, always ready, no error states
* This is your bread and butter input, good for ephemeral client local state, such as search or filtering – you always want latest, there are never conflicts.
* Safe for concurrent writes.
* Used in **TemperatureConverter** tutorial.
* Pure functional!

!fn-src[hyperfiddle.input-zoo0/Input]()

!fn-src[hyperfiddle.input-zoo0/Checkbox]()