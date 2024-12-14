# Tables with virtual scroll (spooling approach)

* High performance server-streamed virtual scroll in like, 10 LOC
* This is only one of many interesting scroll configurations. More to come
* This is not yet battle hardened, if you experience perf issues on your machine please tell us in slack

!ns[electric-tutorial.scroll-spool/WebviewScroll]()

What's happening

* 10,000 records, server streamed as you scroll - database to dom
* non trivial row components with server dependencies and control flow:
  * live picklists (server backed)
  * only visible records are fully realized, the query returns ids only not reecords
  * some IDs are red (if divisible by 10)
* Try holding `Page Down`

Background
* In [Talk: Electric Clojure v3: Differential Dataflow for UI (Getz 2024)](https://hyperfiddle-docs.notion.site/Talk-Electric-Clojure-v3-Differential-Dataflow-for-UI-Getz-2024-2e611cebd73f45dc8cc97c499b3aa8b8), we demonstrated an abstraction safe virtual scroll, which had performance issues, but we said we think we can bring this in line with the performance of the [Electric v2 datomic browser demo](https://electric-datomic-viewer.fly.dev/(:app.datomic-browser!attribute,:abstract%52elease!name), which is fantastic.
* Here, we revisit. And behold, it is now fast!
* Not just fast: **we've *beat* that v2 Datomic browser,** which is highly optimized with lots of boilerplate markup. This demo has *far* simpler markup, literally boiling down to:
  * `(e/for [[i x] (Spool count xs offset limit)] (Row x))` ! 
  * (OMG!!)

Perf wise, what's changed since the talk?

* Electric v3 is now, let's say "3x" faster than it was in August 2024, we dont have formal metrics to share today but early optimizations resulted in strong improvements that can be immediately felt in userland.
* more optimized css - optimizing browser layout is just as important as optimizing IO for demos at this throughput
* general stability/bugfixes, allowing us to express our ideas without electric bugs getting in the way like they did in the talk
* Other than that, it's a naive loop with no fancy tricks, just like the talk

Features
* optimized dom write patterns - rows are bound to a record, DOM is only touched at the edges
  * this enables control flow in row renderer. (Other scroll strategies might use the same N row elements fixed position and scroll the values through them; this saves dom mount/unmount at the cost of recomputing all cells and their business rules, which can cause flicker)
  * Spool recycles row elements (i.e., rows that fall off one end will wrap around and be reused without mount/unmount cost, which currently can be expensive in electric)
* viewport height automatically determined 
* very good network if desired - if the table is server sited, we get differential record loading. if the table is client sited, we send the whole result page down and diff on the client. Whichever is faster probably depends on how many records are loaded (i.e., viewport height).
* supports an `overquery-factor` (e.g. 2x) which reduces the loading artifacts (visible blank rows) when holding down "Page Down" (i.e. playing the tape forward) at low cost
* the code is **Very Crisp** - both user code and the scroll helpers
* random access, i.e. seek/jump to index
  * (Note, random access is kind of not a real requirement of business apps, which are more focused on search than random access. This is really just a perf demo showing what is possible)

Tech specs
* no wrapper divs, no mandatory row markup or inline styles, the `dom/tr` is optional 
  * (note the tr is `display:contents` i.e. removed from layout entirely!)
  * layout is fully under user control - both scroll viewport layout (here, fixed height), and table layout (here, css grid) 
* row layout is NOT quantized (the rows are NOT snapped to a grid like Google Sheets)
* row count and row height must be known at mount time (used to set scrollbar height for random access)
* the resultset must maintain a stable order as it evolves incrementally over time (i.e., sorted)

Synchronous row loading

* the e/for and the (Row) are same sited, so they run synchronously (as described in the talk). 
* That means there is no request waterfall. Which is certainly necessary for performance here.

## How it works

Resultset is realized once and held in memory

* `(let [xs (e/server (query ...))] ...)`
* recall that `let` introduces memoization, `xs` is retained and reused
* it recomputes if the args to `(query ...)` changes, and that's it
* that means, as the scroll offset changes, we do not recompute the query, we simply subvec over the retained result, which is very fast!

!fn-src[contrib.data/window]()

* `xs` will be disposed when the value is no longer used, thanks to Missionary's RAII semantics. "Last person to leave turns off the lights"
* Note that, the query is eager anyway if it contains any JOINs, you have to run all the JOINs before you see the first record, so realizing the entire resultset does not actually cost you anything

Clever ring-buffer using diff-by with modulo key:

!fn-src[hyperfiddle.electric-scroll0/Spool]()

* So, we use `map-indexed` to give each record an index, and then we diff-by with `(mod (first %) limit)`
* this means, as records scroll in and out of view, instead of issuing grow/shrink diffs, we issue change diffs and recycle the row frames like a conveyor belt
* (I don't actually fully understand what is happening here, todo investigate. Is it also rotating the rows? Why does the dom inspector show each row being touched as we scroll? Can we use the css `order` property to rotate rows with fewer dom writes and fewer layouts?)

Limitations and issues with this ring buffer approach (future work)

* resizing the viewport crashes the diff-by with this approach. This demo has a fixed height viewport. (diff-by does not like the diff key changing, here the diff-key depends on limit, the viewport size)
* search/filtering is unstable, also likely connected with the diff-by. (it seems diff-by is very sensitive to how much the collection varies as it evolves, the diff key needs to be tuned to the expected input variance. If the input variance is too high, (e/diff-by {} ...) is the best configuration, (i.e. no key at all), which causes the algorithm to fallback to index-wise changes))
* long jumps (seek to entirely new result page) means the user sees white rows for a few animation frames. Datomic browser does not have this visual artifact, because the "pixel" strategy leaves old rows visible while new rows load. So, despite being slower by the clock, the datomic strategy feels better when seeking/jumping through large resultsets. Note, jump-scroll is kinda not a real use case, what enterprise users actually want is search