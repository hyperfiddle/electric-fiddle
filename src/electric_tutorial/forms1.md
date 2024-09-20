# Input* — crude uncontrolled state, rarely useful

Controlled and uncontrolled inputs.

!fiddle-ns[](electric-tutorial.forms1/Forms1)

* synchronous, local, always succeeds, always ready, no error states
* It's uncontrolled, so there cannot be initial state. (use `Input` instead)
* Several tutorials used this impl inline (webview) but only because is 2 LOC.
* note the div on L2 and the map on L3 – we're playing with values
* note `parse-long` on L8, I like to thread my event processing code because it feels like a callback and lets the view read left to right
* Composite widgets (e.g. a typeahead) might inline and customize a simple impl like this.

* FAQ: Ordering? hash map is unordered?
* e/amb as electric's do - you coulda used do here because the value we want is last, but broadly you want to collect all values. note: dom/text returns (e/amb), so ...

!fn-src[hyperfiddle.input-zoo0/Input*]()

!fn-src[hyperfiddle.input-zoo0/Checkbox*]()