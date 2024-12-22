# Webview <span id="title-extra"><span>

<div id="nav"></div>

A database backed webview with reactive updates.

!ns[electric-tutorial.webview1/Webview1]()

What's happening
* The webview is subscribed to the database, which updates with each transaction.
* filtering (happens on server side)
* differential collection updates - differential wire traffic
* see diffs in console

Callback-free input

Concurrent let

Differential dataflow
* Watch the talk
* streaming diffs, e/as-vec, Tap-diffs, e/amb, products ...

Novel forms
* `e/watch` on datascript connection
* `e/offload` run a blocking function (i.e. query) on threadpool, JVM only
* `e/diff-by`

e/for is applicative only

Key ideas
* Datascript is on the server, it can be any database
* Direct query/view composition, with a loop
* Electric is fully asynchronous, don't block the event loop!
* IO encapsulation – we want `Webview` to be sheltered from knowing/caring that the `Teeshirt-orders` function accesses server data.

See the London Clojurians 2024 talk about this subject.

Why entity API?
* because the collection is differential - we prefer to pay for queries at item granularity. If a single element is added to the collection, we don't want to redo the whole query - just load the incremental record.
* Matters a lot more in the virtual scroll case