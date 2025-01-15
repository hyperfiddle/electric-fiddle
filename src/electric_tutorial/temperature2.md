# e/amb — Temperature Converter <span id="title-extra"><span>

<div id="nav"></div>

* Here we demonstrate `e/amb` as a way to efficiently gather state from concurrent processes

!ns[electric-tutorial.temperature2/Temperature2]()

What's happening
* We've removed the atom, and particularly the `reset!` from all three places. **Now there is only one `reset!`**
* Instead of `reset!`, "events" (kinda) flow upwards towards the app root as *values*
* `e/amb` is being used inside dom containers (e.g. `dl`) to gather concurrent states from concurrent input controls (i.e., **concurrent processes!**)

In this demo, we use `e/amb` to accumulate user interaction state and propagate it to the app root via the return path

* `Input` returns its (singular) state in superposition
* On L20, `e/amb` collects values from the two Inputs (which return singular states i.e. `(e/amb 0)` (degrees celcius)) and the two dom/texts (which return Nothing, i.e. `(e/amb)`)
* resulting in `(e/amb (e/amb) (e/amb 0) (e/amb) (e/amb 0))` = `(e/amb 0 0)`, the state of the two inputs, multiplexed
* In effect, collecting *concurrent* states from the DOM and propagating them back up to the app root via return path.
* `(dom/dd)` returns the last child (recall the dom containers wrap children in implicit `do`)
* similarly, `dom/dt` returns it's last child (the text), and `(dom/text "Celcius")` returns `(e/amb)`

FAQ: Why not use `[]` to collect DOM states instead of `e/amb`?

* Because `[]` is not a differential operator, `e/amb` is reactive at the granularity of each individual element.
* each element of the e/amb is an independent process (here, independent dom elements are modeled as individual processes running concurrently, each returning its state)
* e/amb is also syntactically convenient, giving a new ability to return Nothing (or, nothing *yet*!)