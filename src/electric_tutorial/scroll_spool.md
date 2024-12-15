# Tables with virtual scroll (spooling approach)

* High performance server-streamed virtual scroll in like, 10 LOC
* This is only one of many interesting scroll configurations. More to come
* Try it on your phone!
* Under construction / wip, please send feedback/questions/issue reports in the slack

!ns[electric-tutorial.scroll-spool/WebviewScroll]()

What's happening

* 10,000 records, server streamed as you scroll - database to dom
* non trivial row components with server dependencies and control flow:
  * live picklists (server backed)
  * only visible records are hydrated (the query only gives ids)
  * some IDs are red (if divisible by 10)
* Try holding `Page Down` to "play the tape forward"

Background
* In [Talk: Electric Clojure v3: Differential Dataflow for UI (Getz 2024)](https://hyperfiddle-docs.notion.site/Talk-Electric-Clojure-v3-Differential-Dataflow-for-UI-Getz-2024-2e611cebd73f45dc8cc97c499b3aa8b8), we demonstrated what we hope to be an abstraction safe virtual scroll, which had performance issues at the time of the talk (which was the first preview of Electric v3).
* We said we think we can bring this performance in line with the performance of the [Electric v2 datomic browser demo](https://electric-datomic-viewer.fly.dev/(:app.datomic-browser!attribute,:abstract%52elease!name), which has fantastic performance, though it is highly optimized with about 60 LOC to implement a special scroll strategy.
* Here, we revisit. And behold, the demo from the talk is now fast! 

Not merely fast: **it's *faster* than that v2 demo, we've *beaten* it,** in three ways:
* the viewport takes the whole screen, it is not fixed to 20 rows (which was part of the performance trick!)
* depsite scaling to 10x more rows, the raw performance is *better*
* the implementation is *far simpler*, essentially:
* `(e/for [[i x] (Spool count xs offset limit)] (Row x))`
* So, OMG!!

Perf wise, what's changed since the talk?

* Electric v3 is now, let's say "3x" faster than it was in August 2024, we dont have formal metrics to share today but early optimizations resulted in strong improvements that can be immediately felt in userland.
* more optimized css - optimizing browser layout is just as important as optimizing IO for demos at this throughput
* general stability/bugfixes, allowing us to express our ideas without electric bugs getting in the way like they did in the talk
* Other than that, it's a naive loop with no fancy tricks, just like the talk

Features
* optimized dom write patterns - rows are bound to a record, DOM is only touched at the edges
* viewport height automatically determined
* differential, server-streamed data loading
* supports an `overquery-factor` (e.g. 2x) which reduces the loading artifacts (visible blank rows)
* random access, i.e. seek/jump to index (not that important of a feature in real apps actually, but it makes a good perf demo showing what's possible)
* crisp, simple code — both user code, and the scroll helpers
* synchronous row loading as described in the talk: the e/for and the (Row) are same sited, so they run synchronously without any request waterfall. Which is certainly necessary for performance here
* no mandatory row markup or inline styles, the `dom/tr` is cosmetic (note it is actually `display:contents` here, i.e. removed from layout entirely)
* layout is fully under user control - both scroll viewport layout (here, fixed height), and table layout (here, css grid)

Tech specs
* row layout is NOT quantized (the rows are NOT snapped to a grid like Google Sheets)
* row count and row height must be known at mount time (used to set scrollbar height for random access)
* the resultset must maintain a stable order as it evolves incrementally over time (i.e., sorted)

## How it works

Resultset is realized once and held in memory

* `(let [xs (e/server (query ...))] ...)`
* recall that `let` introduces memoization, `xs` is retained and reused. 
* it recomputes if the args to `(query ...)` changes, and that's it
* that means, as the scroll offset changes, we do not recompute the query, we simply subvec over the retained result, which is very fast!
* Implying that Electric backends are stateful! State is GOOD actually, it is how you avoid computation and make things FAST! Functional programming is not about *avoiding* state, it is about *taming* it so we can reason about it at greater scale!

!fn-src[contrib.data/window]()

* `xs` will be disposed when the value is no longer used, thanks to Missionary's RAII semantics. "Last person to leave turns off the lights"
* Note that relational queries are eager anyway due to JOIN—you have to run all the JOINs to completion before you see the first record—so realizing the entire resultset before paginating does not actually cost you anything!

Spool contains the secret sauce, it recycles row elements using a clever `e/diff-by` with `mod` as the key:

!fn-src[hyperfiddle.electric-scroll0/Spool]()

* Here we use `map-indexed` to give each record an absolute index (here, `0` to `9999`)
* then we diff-by over that index, but with `(mod (first %) limit)`
* this means, as records scroll in and out of view, instead of issuing grow/shrink diffs that will churn the DOM, we issue *change* diffs which *recycle the row frames*, like a conveyor belt. So in other words, rows that fall off one end will wrap around and be reused without mount/unmount cost, which currently are expensive in Electric.
* the absolute index is returned to userland because some layout strategies need it for absolute positioning the rows (this demo doesn't need it, we position a wrapper div instead, the `dom/table`)
* (I don't actually fully understand what is happening inside this diff-by, todo investigate. Can we demonstrate it is actually rotating rows? Why does the dom inspector report each row being touched as we scroll? Can we use the css `order` property to rotate rows with fewer elements touched?)

This conveyor belt approach enables control flow in the row renderer

* In our Electric v2 demos, anywhere where there is a conditional in a cell renderer, there was typically <a href="https://electric-demo.fly.dev/(user.demo-explorer!%44irectory%45xplorer)">visible flicker (v2 dir explorer demo)</a>, which is an artifact of the scroll strategy that leaves the row elements in place and instead slides the *values* through the grid. The optimization here is that by fixing the dom elements statically, you only have to do point text writes to rotate the row values through the pre-existing row elements. But it's not free: the consequence is, if the row markup is dynamic—recall the folder explorer demo, where folders render as hyperlinks but files do not—we now need to teardown and rebuild the `<a>` element (and its underlying Electric DAG/logic) in order to slide it through the grid, and this happens at the animation rate of 120 fps, and Electric can't keep up, so you see flicker.
* The v3 conveyor belt approach solves this by binding row elements to a stable entity identity (i.e. the natural thing that you want), and therefore DOM mutations only happen at the edges. Turning an O(N) cost into O(1). Great!

Commentary: So, is Electric v3 faster than v2, or slower ...?
* "yes"
* Electric rewards programmers who understand what they are asking the computer to do.
* Here there are basically four "processes" coordinating: the database, the server, the client and the DOM
* Here v3 is finally expressive enough (through differential dataflow, electric lambda, dynamic siting, better electric-dom3) that we can express the actual computational structure we have in mind and choreograph the four platforms to do the dance we want them to do.
* Bottom line: here we demonstrate an efficient program. The Electric v3 program feels faster than the v2 program. And yet, Electric v3 in fact is much slower than v2, the differential operators are heavy and memory intensive. But if you use them well, like we have here, it doesn't matter.

Limitations and issues with this ring buffer approach (future work)

* resizing the viewport resets the diff-by (this demo has a fixed height viewport, which is upstream of the `limit` value which is used in the diff key). It seems that diff-by does not like the diff key changing, which makes sense as it will need to reboot its state.
* search/filtering is unstable, also likely connected with the diff-by strategy. (it seems diff-by is very sensitive to how much the collection varies as it evolves, the diff key needs to be tuned to the expected input variance. If the input variance is too high, (e/diff-by {} ...) is the best configuration, (i.e. no key at all), which causes the algorithm to fallback to index-wise changes))
* long jumps (seek to entirely new result page) means the user sees white rows for a few animation frames. Datomic browser does not have this visual artifact, because the "pixel" strategy leaves old rows visible while new rows load. So, despite being slower by the clock, the datomic strategy feels better when seeking/jumping through large resultsets. Note, jump-scroll is kinda not a real use case, what enterprise users actually want is search