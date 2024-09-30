# Temperature2

* Goal: understand dataflow cycles as a pure functional encoding of state

!fiddle-ns[](electric-tutorial.temperature2/Temperature2)

What's happening


* `e/with-cycle`
* `e/amb` and `m/amb`


Control state values propagate back up to the app root via return path
* `(dom/dd)` returns the last child (the macroexpansion puts the children in an implicit do).
* similarly, `dom/dt` returns it's last child (the text), and `(dom/text "Celcius")` returns ... `(e/amb)` i.e. Nothing


Hold my beer ... `(e/amb)` holds an ordered collection of values in superposition
* Jokes aside ... e/amb has become such an important primitive to us that I've pulled forward this discussion, despite this control flow being super advanced.
* e/amb (yes, from [SICP](https://sarabander.github.io/sicp/html/4_002e3.xhtml)) is a multiplexing primitive, traditionally representing parallel evaluation
* `(e/amb)` means Nothing, i.e., zero values in superposition
* `(e/amb 1 2)` holds *both* the 1 and the 2 in superposition, in the same value, at the same time







