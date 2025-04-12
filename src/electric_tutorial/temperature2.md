# e/amb — Temperature Converter <span id="title-extra"><span>

<div id="nav"></div>

* Here we demonstrate `e/amb` as a way to efficiently gather state from concurrent processes

!ns[electric-tutorial.temperature2/Temperature2]()

What's happening
* We've removed the atom, and particularly the `reset!` from all three places. **Now there is only one `reset!`**
* Instead of `reset!`, "events" (kinda) flow upwards towards the app root as *values*
* `e/amb` is being used inside dom containers (e.g. `dl`) to gather concurrent states from concurrent input controls (i.e., **concurrent processes!**)
