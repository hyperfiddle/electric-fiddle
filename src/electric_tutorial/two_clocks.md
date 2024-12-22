# Two Clocks <span id="title-extra"><span>

<div id="nav"></div>

* The easiest way to understand Electric is **streaming lexical scope**.
* Here, the server clock is streamed to the client.

!ns[electric-tutorial.two-clocks/TwoClocks]()

What's happening

* Two clocks, one on the client, one on the server, we compute the skew.
* The server clock streams to the client over websocket.
* The expression is **multi-tier** (i.e., full-stack) – it has frontend parts and backend parts.
* The Electric compiler infers the backend/frontend boundary and generates the full-stack app (concurrent client and server processes that coordinate).
* **Network "data sync"† is automatic and invisible.** (†I do not like the framing "data sync" because it implies *synchronization*, i.e. the reconciliation of two separate mutable stores which have conflicting views as to what is true. This is not how Electric works! More on that later.)
* When a clock updates, the **reactive** view incrementally recomputes to stay consistent, keeping the DOM in sync with the clocks. Both the frontend and backend parts of the function are reactive.

Syntax

* `c` and `s` are both stream and value, you can think of it either way. **streaming lexical scope**! (Technically the underlying FRP datatype is not stream, rather signal.)
* `e/client` `e/server` - these "site macros" are compile time markers, valid in any Electric fn body.
* `e/defn` defines an Electric function, which is reactive. `e/defn` is a macro containing a complete Clojure analyzer and compiler; it compiles this Electric code down to Clojure & ClojureScript target code. That means at runtime there are two processes, a frontend and a backend that communicate by websocket.
* Each Electric expression e.g. `(- c s)` is async/reactive. That means all expressions are recomputed when any input argument updates. There is reactive/FRP type machinery under the hood, provided by [Missionary](https://github.com/leonoel/missionary), which is a **functional effect system** (i.e., framework for concurrency and IO - competitive with core.async).

Network transparency

* Electric functions are **network-transparent**: they transmit data over the network (as implied by the AST) in a way which is invisible to the application programmer. See: [Network transparency (wikipedia)](https://en.wikipedia.org/wiki/Network_transparency)
* The network is reactive too!, at the granularity of individual scope values.
* When server clock `s` updates, the new value is streamed over network, bound to `s` on the client, write through to dom.
* This is not RPC (request/response), that would be too slow. The server streams `s` without being asked, because it knows the client depends on it. If the client had to request each server clock tick, the timer would pause visibly between each request, which would be too slow.
* **Everything is already async, so adding a 10ms websocket delay does not add impedance**, complexity or code weight! This is the central insight of Electric. For a 10min video explainer, see [UIs are streaming DAGs (2022)](https://hyperfiddle.notion.site/UIs-are-streaming-DAGs-e181461681a8452bb9c7a9f10f507991). It's important that you get comfortable with this idea, so please watch the talk!

Reactive DOM

* DOM rendering happens on client, there is no server side rendering. **DOM rendering is effectful**, through point mutations, like `dom/text` here. In v3, the dom macros understand (at runtime) their relative location in your program's runtime DAG and use that knowledge to mount and update themselves in the appropriate place in the DOM. So when `c` updates, `(dom/text "client time: " c)` will issue a point write to the DOM at this expression's corresponding place in the DOM.
* **DOM rendering is free**: there is no React.js, no virtual dom, no reconciler, no DOM diffing. Electric is already fully reactive at the programming language level so there is no need. electric-dom is straightforward machinery to translate Electric change streams into dom mutations.
* If you do need to interop with React.js ecosystem components, it's just javascript, feel free to do so, a React bridge is about 30 lines of code.
* FAQ: **Can you use Electric from ClojureScript only** without a server and websocket (e.g. as a React.js replacement)? Yes, as of v3 this is officially supported!

Electric function call convention

* `(e/System-time-ms)` is an Electric function call. **Electric functions *must* be capitalized (as of v3).**
* Electric is Clojure-compatible, so we have to differentiate Clojure function calls `(f)` from Electric function calls `(F)`. Due to Clojure being dynamically typed, there's no static information available for the compiler to infer the right call convention in this case.
* Reagent has a call convention too; in Reagent we denote component calls with square brackets `[F]` (as React does with JSX `<F>`). This syntax distinguishes between calling component functions vs ordinary fns. To help remember, Reagent/React users capitalize their component functions.
* The Electric compiler, as of v3 makes this convention mandatory. In Electric v3, a capitalized function name (first letter) denotes an Electric call and not a Clojure call. This is an experiment, if it blows up too many pre-existing Clojure macros maybe we revert to v2 syntax `(F.)` or UIX syntax `($ F)`. The compiler maintains a whitelist regex of edge cases, such as gensym's `G__1`.
* FAQ: Why do we need syntax to call Electric fns, Electric has an analyzer, why not just use metadata on the var? A: Because lambdas. Electric expressions can call both Electric lambdas and ordinary Clojure lambdas, e.g. `(dom/On "input" #(-> % .-target .-value) "")`. Electric is a compiler and needs to know the call convention at compile time. Vars are available at compile time, but lambda values are only known at runtime. However, Electric could use var metadata to disambiguate the static call case, which would further reduce collision surface area (todo implement).

Clojure/Script compatibility

* `(- c s)` is a ClojureScript function call (i.e. `-` is ClojureScript's minus).
* Electric is **"99%" Clojure/Script compatible** (i.e., to a reasonable extent - including syntax, defmacro, collections & datatypes, destructuring, host interop, most of clojure.core, etc).
* To achieve this, Electric implements an actual Clojure/Script analyzer and implements all ordinary Clojure special forms.
* That means, most any valid Clojure or ClojureScript expression, when pasted into an Electric body, will evaluate to the same result, and produce the same side effects (though not necessarily in the same statement order).
* Therefore, many pre-existing Clojure/Script macros unaware of Electric will work (e.g. `core.match` works), to the extent that their macroexpansion is referentially transparent (i.e. does not rely on runtime mutation or host concurrency, thread stuff including `future` and `push-thread-bindings`, etc).
* **It's just Clojure!**

Electric is a reactivity compiler

* Electric has a DAG-based reactive evaluation model for fine-grained reactivity. Unlike React.js, Electric reactivity is granular to the expression level, not the function level.
* Electric uses macros to compile actual Clojure syntax into a DAG, using an actual Clojure/Script analyzer inside `e/defn`.
* After performing macroexpansion, Electric essentially **reinterprets** Clojure syntax forms under reactive semantics.
* **Electric is not rewriting your code or tampering with your macroexpansion**, it is *reinterpreting your actual code* under new reactive evaluation rules which, in the case of referentially transparent (RT) expressions, will produce the same output result for the same inputs as the original Clojure evaluation rules (with different runtime time/space tradeoff).
* Under this new interpretation, Electric compiles your program down into very different Clojure/Script target code – a RT Missionary program/expression composed of many thousands of pure functional Missionary operators.

There is an isomorphism between programs and DAGs

* you already knew this, if you think about it – for example, [call graph (wikipedia)](https://en.wikipedia.org/wiki/Call_graph)
* The Electric DAG is an abstract representation of your program
* The DAG contains everything there is to know about the **flow of data ("dataflow")** through the Electric program's **control flow** structures
* Electric uses this DAG to drive reactivity, so we sometimes call the DAG a "reactivity graph".
* But in theory, this DAG is abstract and there could be evaluated (interpreted or compiled) in many ways.
* E.g., in addition to driving reactivity, Electric uses the DAG to drive network topology, which is just a graph coloring problem.