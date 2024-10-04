# e/with-cycle — Temperature Converter

* Same as previous demo with the atom factored out (i.e., pure functional style)
* introduce `e/with-cycle` as a pure functional encoding of state
* introduce `e/amb` as a way to efficiently gather state from concurrent processes

!fiddle-ns[](electric-tutorial.temperature2/Temperature2)

What's happening
* We've removed the atom, and particularly the `reset!` from all three places
* "Events" (control state changes) flow upwards towards the app root as values
* `e/amb` is being used inside dom containers (e.g. `dl`) to gather concurrent states from concurrent input controls (i.e., **concurrent processes!**)

`e/amb` – a key primitive for building UI with Electric

* Hold my beer ... `(e/amb)` holds an ordered collection of values in superposition
* Amb is classically is a multiplexing primitive, representing **concurrent evaluation**
  * as seen in [SICP's amb operator](https://sarabander.github.io/sicp/html/4_002e3.xhtml) and also [Verse](https://simon.peytonjones.org/assets/pdfs/verse-conf.pdf)
* `(e/amb)` means "Nothing", i.e., zero values in superposition
* `(e/amb 1 2)` evaluates to a value representing *both* the `1` and the `2` in superposition, in the same value, at the same time.
* `(e/for [x (e/amb 1 2)] (prn (inc x))` will print `2` `3`. The `e/for` is isolating the branches of the e/amb so you can think about them one at a time.
* `(prn (inc (e/amb 1 2)))` will print `2` `3` — the `prn` ran twice, as did the `inc`! This is called **auto-mapping**. Mostly we recommend you don't do this, use `e/for`.

We call these "amb values," **tables**.

* This is a SQL analogy: most SQL operators, such as `SELECT`, operate on tables not individual values.
* The idea is also reminiscent This is a similar concept as "vector" from [vector programming languages](https://en.wikipedia.org/wiki/Array_programming) such as MATLAB, but (speaking to MATLAB) it is not quite the right concept, as in mathematics, vectors store quantities that have a specific relation between them such that they transform in a specific way under changes in coordinates such as rotations. Electric tables are not this.
* All electric expressions and scopes (lexical and dynamic) evaluate/resolve to tables that hold zero or more values in superposition (typically one).

Tables are what `e/diff-by` returns, and what `e/for` iterates over.

* `(e/diff-by identity [1 2])` constructs an Electric table from a Clojure collection. When the clojure collection evolves over time (e.g. from successive database query results), the key function (e.g. `identity`) is used to stabilize the lifecycle of branches of the amb, which matters if there are effects hooked onto branches via the diff lifecycle, for example to materialize a reactive collection into the DOM.
* `(e/amb 1 2 (e/amb 3) (e/amb))` flattens to `(e/amb 1 2 3)`

How is the table `(e/amb 1 2)` different from the Clojure vector `[1 2]`?

* Electric tables are incrementally maintained data structures and are reactive at the granularity of the individual element.
* I.e., Electric tables represent concurrent processes that produce updated states independently.
  * e.g. `(prn (e/amb 42 (e/System-time-ms)))`
* so we want to ensure incremental updates, and only compute `prn` on the **changeset**
* `(e/as-vec (e/amb 1 2))` returns `[1 2]` - **materializing** the Clojure vector from the incremental Electric table (by reducing over the flow of diffs!)

In this demo, we use `e/amb` to accumulate user interaction state and propagate it to the app root via the return path

* `Input` returns its (singular) state in superposition
* On L20, `e/amb` collects values from the two Inputs (which return singular states i.e. `(e/amb 0)` (degrees celcius)) and the two dom/texts (which return Nothing, i.e. `(e/amb)`)
* resulting in `(e/amb (e/amb) (e/amb 0) (e/amb) (e/amb 0))` = `(e/amb 0 0)`, the state of the two inputs, multiplexed
* In effect, collecting *concurrent* states from the DOM and propagating them back up to the app root via return path.
* `(dom/dd)` returns the last child (the macroexpansion puts the children in an implicit `do` - which we may replace with e/amb, to remove boilerplate from e.g. this demo!)
* similarly, `dom/dt` returns it's last child (the text), and `(dom/text "Celcius")` returns `(e/amb)`

FAQ: Why not use `[]` to collect DOM states instead of `e/amb`?
* Because `[]` is not a differential operator, `e/amb` is reactive at the granularity of each individual element.
* each element of the e/amb is an independent process (here, independent dom elements are modeled as individual processes running concurrently, each returning its state)
* e/amb is also syntactically convenient, giving a new ability to return Nothing (or, nothing *yet*!)

FAQ: Why is `e/amb` built into the language?
* Because the network wire protocol needs to be aware of the diffs, and this impacts the backpressure semantics of the language itself.
* Electric v2's continuous time work skipping semantics were incompatible with diff flows, because dropped values or duplicate values—which are fine in continuous time—are illegal with diff flows. Each diff must be accounted for exactly once.
* Therefore, the Electric v3 computational structure promotes diff flows to a central construct, requiring us to essentially build the differential collection type (e/amb) directly into the language evaluation model, effectively vectorizing the language.
* This is discussed in [Electric Clojure v3: Differential Dataflow for UI (Getz 2024)](https://hyperfiddle-docs.notion.site/Talk-Electric-Clojure-v3-Differential-Dataflow-for-UI-Getz-2024-2e611cebd73f45dc8cc97c499b3aa8b8).

`e/with-cycle`

* this reifies the `(reset! !x (f (e/watch !x)))` idiom into a "pure functional" loop operator, reminiscent of Clojure's `loop/recur`.
* In Haskell see [RecursiveDo](https://ghc.gitlab.haskell.org/ghc/doc/users_guide/exts/recursive_do.html) for recursive bindings in certain monadic computations, such as continuous time dataflow computations.

!fn-src[hyperfiddle.electric3/with-cycle]()

* we could subsitute a pure implementation of with-cycle – for example, possibly using function recursion to implement the loop (we would need something akin to tail recursion to ensure we reuse the same reactive "frame" each iteraction, which the atom implementation correctly does out of the box, which is why we do it.)
* Lesson: dataflow cycles are isomorphic to state; this is a pure functional pattern actually! Remember, the reactive engine's implementation is imperative for performance, that does not mean the language operators are impure! Electric's core operators are pure! This operator is pure!

Differential `e/amb` (this is important)

* Note that `(reset! !m (e/amb form1 form2))` is a differential product. What's happening? Why does it work?
* Recall that e/amb superposition is *reactive* and *differential* – that means, only *changes* propagate.
* When the top form state changes, the e/amb will collect the values (or from the differential perspective – the *changes*), and propagate that new value to the `e/with-cycle`.
* `(e/with-cycle [m state0] (e/amb (UserForm m) (UserForm m)))` will loop that top form's updated state back into *both* forms. The top form receives it's *own* changed state, which is the same as the current state so it reaches a fixed point and halts.
* The bottom form receives the changed state, which is NOT the same as the current state, so it absorbs the new state, and then closes the circuit by emitting the new state, which propagates into the `e/with-cycle`, which is now a fixed point so the computation halts.
* This is a pure functional pattern!

* with-cycle will loop until the input reaches a fixed point, i.e. `m == (UserForm m)` — the value passed into the form is the same as the value it returns.
* Of course, this is a multiplexed form with e/amb, so ...