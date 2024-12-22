# Directory Explorer <span id="title-extra"><span>

<div id="nav"></div>

Demo of Electric v3 showcasing performance and abstraction power at the same time.

* **Server-streamed virtual scroll in ~50ish LOC**
* View grid in <a href="/electric-tutorial.explorer!DirectoryExplorer/">fullscreen mode here</a>
* Try it on your phone!
* It's not quite 100% stable - it crashes if you scroll really hard - electric v3 has a few bugs left. But note that this is a production stress test that is pushing electric very hard! Broadly, we think Electric 3 is fine for internal tools today.
* There may also be userland layout bugs/imperfections (I saw a couple today while writing), please report them and we will get them fixed.
* Please let us know what the performance is like for you, this is our first public virtual scroll demo in v3.

<div style="margin-bottom: 2em;"></div>

!target-nochrome[electric-tutorial.explorer/DirectoryExplorer]()

<div style="margin-bottom: 2em;"></div>

What's happening
* File system browser
* 1000s of records, server streamed as you scroll - database to dom
* Try holding Page Down to "play the tape forward"
* It's very fast, faster than the v2 datomic browser, with larger viewports, and more complex markup
* nontrivial row rendering - tree indentation, hyperlinks, custom row markup
* links work (try it) with inline navigation, **browser history, forward/back, page refresh**, etc! Cool
  * the explorer router even composes seamlessly with the tutorial router
  * in fact, the explorer (an electric function) literally composes with the tutorial (an electric function)
  * **"App as a Function"**

Background
* In [Talk: Electric Clojure v3: Differential Dataflow for UI (Getz 2024)](https://hyperfiddle-docs.notion.site/Talk-Electric-Clojure-v3-Differential-Dataflow-for-UI-Getz-2024-2e611cebd73f45dc8cc97c499b3aa8b8), we demonstrated what we hope to be an abstraction safe virtual scroll, which had performance issues at the time of the talk (which was the first preview of Electric v3).
* We said we think we can bring this performance in line with the performance of the previous Electric v2 datomic browser demo, which had fantastic performance, though it was highly optimized with 60 LOC to implement a special scroll strategy.
* Here, we revisit this. And behold, the demo from the talk is now fast!

Not merely fast: **it's *faster* than that v2 demo, we've *beaten* it,** in three ways:

* The <a href="/electric-tutorial.explorer!DirectoryExplorer/">fullscreen mode viewport will take the whole screen</a>, it is not fixed to 20 rows like the v2 datomic browser was
* depsite scaling to 10x more visible rows, the raw performance is *better*
* the implementation is *far simpler*, boiling down to a simple `e/for`:
  * `(e/server (e/for [i (IndexRing limit offset)] (Row i (nth xs! i nil))))`
  * (`IndexRing` is recycling rows instead of rebuilding them, which is a perf trick and also eliminate layout shifts – though it is not strictly necessary here.)
* So, OMG!!

Perf wise, what's changed since that talk?

* Electric v3 is now, let's say "3x" faster than it was in August 2024, we don't have formal metrics to share today but early optimizations resulted in strong improvements that can be immediately felt in userland.
* general stability/bugfixes, allowing us to express our ideas without electric bugs getting in the way like they did in the talk
* Optimized css - optimizing browser layout is just as important as optimizing network for demos at this throughput

Performance notes

* **This is not our fastest demo, this demo is optimized for simple code.**
* the row-blinking artifacts are caused by what seems to be a spurious round trip, it looks like an Electric issue.
* It can be worked around here by setting `:overquery-factor` to 3, i.e., loading extra 1 page both above and below the viewport. This almost entirely resolves the row blinking when scrolling sequentially (especially on mobile), at the cost of some page load time in fullscreen mode when the page size is large, which is why we've left it off here.
* We'll release an even faster demo soon - we have an internal POC that adds a bit of code complexity to achieve hardware accelerated scroll performance – which blew me away when I first experienced it! Very exciting

90 LOC including css

!ns-src[electric-tutorial.explorer/DirectoryExplorer]()

* crisp, simple code — both user code, and the scroll helpers
* differential, server-streamed data loading
* optimized dom write patterns - rows are bound to a record, DOM is only touched at the edges
* synchronous row loading as described in the talk: the e/for and the (Row) are same sited, so they run synchronously without any request waterfall. Which is certainly necessary for performance here
* viewport height automatically determined
* scroll helpers in [hyperfiddle.electric-scroll0](https://github.com/hyperfiddle/electric/blob/master/src/hyperfiddle/electric_scroll0.cljc)
* [datafy-fs (filesystem as data)](https://gist.github.com/dustingetz/681dcbf16d104b1496a29f2f08965fc8)

Row renderer can query the server using Electric, see `Row` and `Render-cell` above

* no mandatory row markup or inline styles, the `dom/tr` is cosmetic (note it is actually `display:contents` here, i.e. removed from layout entirely)
* layout is fully under user control - both scroll viewport layout and table/grid layout

Tech specs
* row count and row height must be known at mount time (used to set scrollbar height for random access)
* random access, i.e. seek/jump to index (not that important of a feature in real apps actually, but it makes a good perf demo showing what's possible)
* supports an `overquery-factor` (e.g. 2x) which reduces the loading artifacts (visible blank rows)
* row layout is NOT quantized (i.e. the rows are NOT snapped to a grid). We can do this with 1 line of CSS but unclear what the perf impact is, it depends on the layout and IO strategy

Future work
* hardware accelerated scroll configuration (which adds some LOC to carefully optimize the DOM write patterns and carefully optimize CSS layout) - we have it already as a POC, todo write it up and publish
* `; workaround glitch on tutorial navigate (nested router interaction)` - electric v3 has one or two glitch bugs left, active wip
* `; fixme blinks on switch due to unexplained latency - electric issue?` - we suspect an interaction with an edge case in the network protocol, todo investigate

## Special Bonus:

* Did you notice that the code is very nearly generic?
* Take another look at the code. Notice the composition structure.
* Do you think you could generalize this into an abstract data browser? Of course you could.
* The whole point of building Electric was to pave the way for exactly that: a general purpose web-based data browser — i.e., *general hypermedia client* — that is powerful enough to express bespoke business applications, in their full glory: without sacrificing complexity, customization, or performance requirements.
* Based on this result, it seems that with v3, Electric has finally achieved sufficiently strong operational properties to achieve this goal! 🎉🎉🎉🥳🥳🎸🎸 (I hope it was worth the $2M that we spent!)
* [Join our beta](https://www.hyperfiddle.net/early-access.html) if you'd like to take a whack at it! Maybe you beat us to it?
