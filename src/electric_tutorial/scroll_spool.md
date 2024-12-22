# Tables with virtual scroll (spooling approach) (DRAFT)

* todo

!ns[electric-tutorial.scroll-spool/WebviewScroll]()



* the resultset must maintain a stable order as it evolves incrementally over time (i.e., sorted)

## How it works (DRAFT)

**NOTE: this section is already out of date as of 2024 Dec 18, we've made many improvements to the implementation. Todo rewrite the essay.**

Resultset is realized once and held in memory

* `(let [xs (e/server (query ...))] ...)`
* recall that `let` introduces memoization, `xs` is retained and reused. 
* it recomputes if the args to `(query ...)` changes, and that's it
* that means, as the scroll offset changes, we do not recompute the query, we simply subvec over the retained result, which is very fast!
* Implying that Electric backends are stateful! State is GOOD actually, it is how you avoid computation and make things FAST! Functional programming is not about *avoiding* state, it is about *taming* it so we can reason about it at greater scale!

!fn-src[contrib.data/window]()

* `xs` will be disposed when the value is no longer used, thanks to Missionary's RAII semantics. "Last person to leave turns off the lights"
* Note that relational queries are eager anyway due to JOIN—you have to run all the JOINs to completion before you see the first record—so realizing the entire resultset before paginating does not actually cost you anything!

Spool contains the secret sauce, it implements a **ring buffer** that recycles row elements, by using `mod` as the `e/diff-by` key:

!fn-src[hyperfiddle.electric-scroll0/Spool]()

* Here we use `map-indexed` to give each record an absolute index (here, `0` to `9999`)
* then we diff-by over that index, but with `(mod (first %) limit)`
* this means, as records scroll in and out of view, instead of issuing grow/shrink diffs that will churn the DOM, we issue *change* diffs which *recycle the row frames*, like a conveyor belt. So in other words, rows that fall off one end will wrap around and be reused without mount/unmount cost, which currently are expensive in Electric.
* the absolute index is returned to userland because some layout strategies need it for absolute positioning the rows (this demo doesn't need it, we position a wrapper div instead, the `dom/table`)
* (I don't actually fully understand what is happening inside this diff-by, todo investigate. Can we demonstrate it is actually rotating rows? Why does the dom inspector report each row being touched as we scroll? Can we use the css `order` property to rotate rows with fewer elements touched?)

This conveyor belt approach enables control flow in the row renderer

* In our Electric v2 demos, anywhere where there is a conditional in a cell renderer, there was typically visible flicker (v2 dir explorer demo), which is an artifact of the scroll strategy that leaves the row elements in place and instead slides the *values* through the grid (causing row contents to be rebuilt each tick!). The optimization here is that by fixing the dom elements statically, you only have to do point text writes to rotate the row values through the pre-existing row elements. But it's not free: the consequence is, if the row markup is dynamic—recall the folder explorer demo, where folders render as hyperlinks but files do not—we now need to teardown and rebuild the `<a>` element (and its underlying Electric DAG/logic) in order to slide it through the grid, and this happens at the animation rate of 120 fps, and Electric can't keep up, so you see flicker.
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