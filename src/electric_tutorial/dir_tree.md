# Dir Tree <span id="title-extra"><span>

<div id="nav"></div>

* Recursive traversal over directory structure on the server, with in-flight rendering to a recursive DOM view on the client, all in one function.
* This is an example of something that is easy to do in Electric but hard to do with competing technologies.
* Goal: get comfortable with Electric v3's client/server transfer semantics in a more interesting topology.

!ns[electric-tutorial.dir-tree/DirTree]()

What's happening

* client-side rendering the contents of the `src/hyperfiddle` directory (a server resource)
* client-side filtering through the dom/input - try it
* File system I/O is done with standard Java operators
* `Dir-tree*` is a **single-pass recursive function**, it both traverses the directory tree and renders it to the client in a single pass
* The view code deeply nests client and server calls, arbitrarily, even through loops and recursion. The control flow switches between client and server as if it didn't matter – because it doesn't! The Electric compiler figures it out.
* How would you even approach this with GraphQL, REST, or some other dataloader abstraction?

Performance note: there is some lag here! Why does it load in "stratas"? Do we need to be concerned about round trips?
* Yes, this is a request waterfall and it is an implementation flaw. Yes, it will be fixed.
* The Electric DAG captures statically the information needed to batch and essentially pre-send the information that the client is going to ask for.
* But we haven't implemented this yet, basically we have `;todo static lookahead` throughout the network runtime. Soon!
* Our experience running v2 in production is that it was plenty fast in real apps. But, performance is important to us, and we will likely work on this next!
* If you need to optimize this we can show you how. Probably use `tree-seq` to flatten the tree in Clojure, which is what we did in this <a href="https://electric-demo.fly.dev/(user.demo-explorer!%44irectory%45xplorer)">Electric v2 tree view</a>, which is pretty good! (The flicker in that demo is fixed in v3)

Client/server transfer - the basics

* Only values can be serialized and moved across the network. Reference types (e.g. atoms, database connections, Java classes) are unserializable and therefore cannot be moved.
* `(Dir-tree* h s)` is called with both server and client values, which pass through the Electric function call into the body without network transfer.

**"Platform interop is sited, edges are not"** (remember this!)

* Values transfer over network only when platform interop requires it. (Platform interop = foreign Clojure/Script code)
* "Site" is which site (e.g. client or server) an expresison evaluates on.
* the java file handle `h` is server-sited because `java.nio.file.Path/of`, `clojure.java.io/file` etc are server-sited. Note, as it is a reference type, `h` is not serializable, it cannot cross to the client even if you wanted it to.
* `(e/server (.getName h))` passes `h` to Clojure interop `.getName`, which forces `h` to be server-sited, which it already is because it came from `(e/server ... (java.nio.file.Path/of ...))`
* `(dom/text _name)` macroexpands to something like `(e/client (set! (.-textContent e) arg))`; the `set!` (Clojure interop) is what forces the transfer of `_name`.
* (This model is not request/response or RPC; that would be too slow. The transfer is coordinated and planned ahead of time by the compiler/DAG. See: [UIs are streaming DAGs (Getz 2022)](https://hyperfiddle.notion.site/UIs-are-streaming-DAGs-e181461681a8452bb9c7a9f10f507991)

Edges are not sited in v3!
* **Edges** (i.e., in the DAG) are the connections between a shared expression named with `let` and it's consumers. E.g., `h` on L23 is consumed in 4 places: `(.getName h)`, `(.listFiles h)` etc. Each of these 4 connections is an edge in the DAG.
* Edges are not inherently sited! Just because on L22, let binding `s` is created in a server block, does not move `s` to the client! `s` passes through `(Dir-tree* h s)` symbolically, it is not moved unless forced by platform interop, as on L17 `(includes-str? name_ s)`.
* Electric `let`, conceptually, is abstract symbolic plumbing; we do NOT want the fact that we assigned a symbolic name to an expression’s result, to infect the result itself by changing it’s site!
* **Accidental transfer** was a huge issue in Elecric v2 that is now completely gone in v3 due to these semantics. See discussion here: [Electric Clojure v3 teaser: improved transfer semantics (Getz 2024)](https://hyperfiddle-docs.notion.site/Electric-Clojure-v3-teaser-improved-transfer-semantics-2024-735b10c3a0dc424e93e060a0a3e80226)

Electric `let` is **concurrent** in v3
* `let` supports client and server bindings in the same let, as here L22.
* These two bindings "happen" on different machines!
* That means they are concurrent, there is no sequencing or causality between concurrent let bindings unless explicit in the DAG.
* This is a departure from Clojure semantics, where expressions bound in a let are executed in statement order. This change is backwards compatible to the extent that your Clojure code is referentially transparent.

Dynamic siting (new in v3)
* `e/server` and `e/client` are inherited through dynamic scope (in the absence of an explicit site on the expression).
* (In v2 site inheritance was lexical, which caused problems, this fixes them.)
* I don't think the difference matters here in this demo, other than it enables "auto-siting" syntax sugar:

electric-dom macros auto-site their contents (syntax sugar)
* FAQ: L12 `dom/li` is in server scope. Shouldn't that be wrapped in e/client? A: In v3, the dom macros will automatically insert an `e/client` around the underlying dom mutations in the macroexpansion. In v2 this was not automatic, you had to explicitly site your dom expressions.
* Here is the implementation of dom element construction:

!fn-src[hyperfiddle.electric-dom3/With-element]()

* On L10, note the `Body` continuation expression (containing the element's children) is **unsited**! The dom macros carefully site their own effects without altering the site of their children (in the continuation), which stays the same (i.e., inherits the application expression's site through dynamic scope).
* In `Dir-tree*`, the `dom/li` on L12 is server-sited, which means (after inserting the `li` on the side) the `dom/ul` will remain server-sited, and then the `e/for-by` and `(.listFiles h)` are server-sited.
* We call this the "site-neutral style" — where the library programmer takes care to implement certain components in a portable way that allow the application programmer to control the site of parts of the component's implementation. We'll discuss this in the Typeahead tutorial (todo).

So, what's the final network topology here?

* Explained in depth in [Talk: Electric Clojure v3: Differential Dataflow for UI (Getz 2024)](https://hyperfiddle-docs.notion.site/Talk-Electric-Clojure-v3-Differential-Dataflow-for-UI-Getz-2024-2e611cebd73f45dc8cc97c499b3aa8b8)

