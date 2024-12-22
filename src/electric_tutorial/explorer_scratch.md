
Background
* In [Talk: Electric Clojure v3: Differential Dataflow for UI (Getz 2024)](https://hyperfiddle-docs.notion.site/Talk-Electric-Clojure-v3-Differential-Dataflow-for-UI-Getz-2024-2e611cebd73f45dc8cc97c499b3aa8b8), we demonstrated what we hope to be an abstraction safe virtual scroll, which had performance issues at the time of the talk (which was the first preview of Electric v3).
* We said we think we can bring this performance in line with the performance of the previous Electric v2 datomic browser demo, which had fantastic performance, though it was highly optimized with 60 LOC to implement a special scroll strategy.
* Here, we revisit this. And behold, the demo from the talk is now fast!

Not merely fast: **it's *faster* than that v2 demo, we've *beaten* it,** in three ways:

* The <a href="/electric-tutorial.explorer!DirectoryExplorer/">fullscreen mode viewport will take the whole screen</a>, it is not fixed to 20 rows like the v2 datomic browser was
* depsite scaling to 10x more visible rows, the raw performance is *better*
* the implementation is *far simpler*, boiling down to a simple `e/for`:
    
    * ()
* So, OMG!!

Perf wise, what's changed since that talk?

* Electric v3 is now, let's say "3x" faster than it was in August 2024, we don't have formal metrics to share today but early optimizations resulted in strong improvements that can be immediately felt in userland.
* general stability/bugfixes, allowing us to express our ideas without electric bugs getting in the way like they did in the talk
* Optimized css - optimizing browser layout is just as important as optimizing network for demos at this throughput

Tech specs
* no mandatory row markup or inline styles, the `dom/tr` is cosmetic (note it is actually `display:contents` here, i.e. removed from layout entirely)
* layout is fully under user control - both scroll viewport layout and table/grid layout
* random access, i.e. seek/jump to index (not that important of a feature in real apps actually, but it makes a good perf demo showing what's possible)
* row count and row height must be known at mount time (used to set scrollbar height for random access)
* viewport height automatically determined
* supports an `overquery-factor` (e.g. 2x) which reduces the loading artifacts (visible blank rows)
* row layout is NOT quantized (i.e. the rows are NOT snapped to a grid). We can do this with 1 line of CSS but unclear what the perf impact is, it depends on the layout and IO strategy

* The blinking can be worked around by setting `:overquery-factor` to 3, i.e., loading extra 1 page both above and below the viewport and causing the blinking to occur offscreen. However this increases the initial load time in the fullscreen configuration (high number of rows), which is why we've left it off for this demo.