# Uncontrolled forms

* `Input*` models **crude uncontrolled state**.
* This pattern is rarely useful. The `*` suffix is meant to make you ask, "am I sure I should be using this?"
* Documented here for teaching/warmup - you should understand this impl first (it's trivial) as a basis in order to understand what's wrong with it and why the upcoming controls are better.

!fiddle-ns[](electric-tutorial.forms1-uncontrolled/Forms1-uncontrolled)

What's happening

* a form with 3 fields (string/text, long/numeric, bool/checkbox)
* the numeric control has the native inc/dec affordance - hover
* uncontrolled - so there cannot be initial state, there can only be 1 form instance. (For controlled forms, use `Input` instead - next tutorial)
* the field states are aggregated into a form state (a map) which is then printed

Why / what for

* singleton fields with **synchronous, client-local edits** (no latency so no dirty or busy states), that **always succeed** (no failure/retry states), and are **always ready to accept input** (no conflict states states)
* rarely useful, the controlled input encompasses this use case
* not conflict safe - it's a singleton, how can there even be a conflict?
* Tutorial usage: <a href="http://localhost:8080/(electric-tutorial.tutorial!%54utorial)/(electric-tutorial.dir-tree!%44ir%54ree)">dir-tree</a>, <a href="http://localhost:8080/(electric-tutorial.tutorial!%54utorial)/(electric-tutorial.webview1!%57ebview1)">webview</a> (the 2 LOC impl is inlined)

How it works
* field and form states return signals of their current state as reactive values
* note the hashmap on L9 – field states as values, form state as value
* `e/amb` L10 is used to collect state from the dt and dd.
  * The `dom/text` returns `e/amb`, so the `dom/dt` returns `e/amb`.
  * The `dom/dd` returns the input state.
  * The outer `e/amb` therefore evaluates to `(e/amb (e/amb) input-state)` which flattens to only the input signal.
  * `do` is acceptable here because only the final child returns state, however that is error prone since any child may return state, so I recommend you use `e/amb` everywhere to collect state.
  * Maybe `dom/dl` and all dom containers should automatically aggregate all state. We will try this soon. As of this writing (2024 September), dom containers evaluate children with implicit `do`.
* the numeric requires parsing in userland, because that's how the DOM works.
* dom props (e.g. :type number) are passed to the dom as dom/props, so you get the numeric controls.
* FAQ: Ordering? hash map is unordered? A: Um, the reader controls construction but I think this is ordered for <= 8 elements per Clojure internals. Anyway we're going to leave this pattern behind in a moment.

`Input*` implementation (you should understand this before moving forward):

!fn-src[hyperfiddle.input-zoo0/Input*]()