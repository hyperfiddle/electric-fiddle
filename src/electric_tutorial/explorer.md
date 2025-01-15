# Directory Explorer <span id="title-extra"><span>

<div id="nav"></div>

* Server-streamed virtual scroll in **~50ish LOC**
* View grid in <a href="/electric-tutorial.explorer!DirectoryExplorer/">fullscreen mode here</a>. 
* Try it on your phone!

<div style="margin-bottom: 2em;"></div>

!target-nochrome[electric-tutorial.explorer/DirectoryExplorer]()

<div style="margin-bottom: 2em;"></div>

What's happening
* File system browser
* 1000s of records, server streamed as you scroll - file system to dom.
* Try holding Page Down to "play the tape forward"
* It's very fast, faster than the v2 datomic browser, with larger viewports, and more complex markup
* nontrivial row rendering - hierarchy with indentation, hyperlinks, custom row markup
* links work (try it) with inline navigation, **browser history, forward/back, page refresh**, etc! Cool
* Optimized DOM write patterns: DOM is only touched at the edges. Open element inspector and see!

## Source code overview

!ns-src[electric-tutorial.explorer]()

* crisp, simple code — both user code, and the scroll helpers
* scroll helpers in [hyperfiddle.electric-scroll0](https://github.com/hyperfiddle/electric/blob/master/src/hyperfiddle/electric_scroll0.cljc)
* [datafy-fs (filesystem as data)](https://gist.github.com/dustingetz/681dcbf16d104b1496a29f2f08965fc8)

Differential, server-streamed data loading

* Take a closer look at `TableScroll` impl on L40.
* The important bit is the `e/for` on L46, essentially:
* `(e/server (e/for [i (IndexRing limit offset)] (Row i (nth xs! i nil))))`
* This is the essence of a server-streamed virtual scroll: 
  * we use `offset` and `limit` (from the client), 
  * send them up, spawn or rotate rows on the server using `IndexRing` (todo explain), 
  * `(nth xs! i nil)` looks up the file based on the index *on the server*, 
  * `(Row x)` enters the `Row` function and continues into `Render-cell`, running all reachable server scopes as far as possible, 
  * finally broadcasting to the client that `(Row x`) has updated, automatically including all the server state the client is about to need,
  * ultimately the client runs the client scopes and incrementally maintains the collection in the DOM.
* **All this expressed in that single `e/for` expression, 1-2 LOC!**

Server streams the data that the client needs without being asked, i.e. without request waterfalls

* `Row` and `Render-cell` switch between `e/client` and `e/server` to fetch the data they need **inline**!
* Even with inline `e/server` expressions, the rows load without request waterfalls as described in [Talk: Electric Clojure v3: Differential Dataflow for UI (Getz 2024)](https://hyperfiddle-docs.notion.site/Talk-Electric-Clojure-v3-Differential-Dataflow-for-UI-Getz-2024-2e611cebd73f45dc8cc97c499b3aa8b8).
* This is because the `e/for` and `(Row i x)` are same-sited, so they run synchronously: the server enters the Row renderer frame and optimistically sends the data dependencies that it knows the client is about to ask for. Which is certainly necessary for performance here, any waterfall in this code path is very obvious.

## Our goal with Electric: zero-cost abstraction over network

* Did you notice that the code is very nearly generic?
* Take another look at the code. Notice the composition structure.
* Do you think you could generalize this into an abstract data browser? Of course you could.
* The whole point of building Electric was to pave the way for exactly that: a general purpose web-based data browser — i.e., *general hypermedia client* — that is powerful enough to express bespoke business applications, in their full glory: without sacrificing complexity, customization, or performance requirements.
* Based on this result, it seems that with v3, Electric has finally achieved sufficiently strong operational properties to achieve this goal! 🎉🎉🎉🥳🥳🎸🎸 (I hope it was worth the $2M that we spent!)
* [Join our beta](https://www.hyperfiddle.net/early-access.html) if you'd like to take a whack at it! Maybe you beat us to it?
