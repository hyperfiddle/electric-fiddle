# e/amb — concurrent values in superposition

* Amb is classically is a multiplexing primitive, representing **concurrent evaluation**, as seen in [SICP's amb operator](https://sarabander.github.io/sicp/html/4_002e3.xhtml) and also [Verse](https://simon.peytonjones.org/assets/pdfs/verse-conf.pdf).
* We've found that in UI, `(e/amb)` is an important primitive for representing concurrent reactive processes, and it is foundational in Electric v3.
* If you understand most of this page, you'll know enough to understand the <a href="/tutorial/form_explainer">form tutorial</a> where we use `e/amb` to collect concurrent form edit commands from the user.

!fn[electric-tutorial.inputs-local/DemoInputCircuit-amb]()

What's happening
* The two inputs are bound to the same state `s`, but how?
* `s'` is a "table" (i.e., ambiguous "multi-value").
* This table `s'` contains two values in superposition!, almost like a tuple
* There is only one `reset!`, `(reset! !s s')`, which is run when *either* or *any* of the values in table `s'` change!

`(e/amb)` holds an ordered collection of values in superposition

* **superposition:** `(e/amb 1 2)` evaluates to a value representing *both* the `1` and the `2` in superposition, in the same value, at the same time.
* **flattening:** `(e/amb 1 (e/amb 2 3) (e/amb))` evaluates to `(e/amb 1 2 3)`
* **Nothing:** `(e/amb)` means "Nothing", i.e., zero values in superposition
* **Singular values are lifted:** `(println 1)` and `(println (e/amb 1))` are identical, as are `(let [x 1] (println x))` and `(let [x (e/amb 1)] (println x))`

Auto-mapping

* `(inc (e/amb 1 2))` evaluates to `(e/amb 2 3)` - the `inc` is auto-mapped over the table
* `(prn (inc (e/amb 1 2)))` will print `2` `3` — the `prn` ran twice, as did the `inc`! This is called **auto-mapping**
* In the form example, `(reset! !s s')` will run the expression *for each* value in table `s'`

Why are amb values called "tables" in Electric?

* This is a SQL analogy: most SQL operators, such as `SELECT`, operate on tables/sets of records, not individual records.
* This is a similar concept as "vector" from [vector programming languages](https://en.wikipedia.org/wiki/Array_programming) such as MATLAB, but (speaking to MATLAB) it is not quite the right concept. In mathematics, vectors store related quantities which transform together under changes in coordinate basis, such as rotations. Electric tables are not this.
* All electric expressions and scopes (lexical and dynamic) evaluate/resolve to tables that hold zero or more values in superposition (typically one).

Tables are what `e/diff-by` returns, and what `e/for` iterates over.

* `(e/diff-by identity [1 2])` evaluates to `(e/amb 1 2)`
* `(e/for [x (e/amb 1 2)] (prn (inc x))` will print `2` `3`.
  * The `e/for` is isolating the branches of the e/amb so you can think about them one at a time.

Tables are reactive at element granularity, unlike clojure vectors/tuples

* i.e., tables are differential collections
* so, `(println (e/amb 1 (e/watch !b)))` will print twice (`1` and `b`) on initial boot, and subsequently never print `1` again, printing only `b` when it changes
* in other words, `println` will auto-map across the table, and the **auto-mapping happens element-wise on *changes*!**

How is the table `(e/amb 1 2)` different from the Clojure vector `[1 2]`?

* Electric tables are incrementally maintained data structures and are reactive at the granularity of the individual element.
* use `e/as-vec` to **materialize** a `clojure.core/vector` from the incremental Electric table (by reducing over the flow of diffs!)
  * `(e/as-vec (e/amb 1 2))` returns `[1 2]`
* Once you have a Clojure vector, you've **lost reactivity at element granularity**, so there's really not much you can do with it other than print it (i.e. debugging), or use `e/diff-by` to diff it back into a reactive collection.

**Tables are not a data type**, they are built into the language itself as part of it's evaluation model.

* In `(let [x (e/amb 1)] ...)`, table `x` is a differential collection of length 1
* In `(let [x 1] ...)` — this expression is identical! literal `1` is auto-lifted into a table, and table `x` is a differential collection of length 1.
* Why? Because Electric's network wire protocol needs to be aware of the diffs, and this impacts the backpressure semantics of the language itself.

Given the language's auto-mapping semantics, what is `e/for` then? Is there any difference between
* `(let [x (e/amb 1 2)] (println x))` and
* `(e/for [x (e/amb 1 2)] (println x))` ?

YES, two major differences in semantics. Consider the more interesting:
```
(e/for [x (e/amb 1 2)]
  (let [!a (atom 0)]
    (reset! !a x)))
```
* Two atoms are created, each is reset once: `e/for` mounts a branch with `x` bound to `(e/amb 1)` and another branch with `x` bound to `(e/amb 2)`.
* Now replace `e/for` with `let`, you get two resets on a single atom.
* So, `e/for` affects **resource allocation** semantics.

The second difference occurs when there is an `if` in the control form's body. Electric `if`'s current implementation interacts badly with auto-mapping semantics:

* `if` produces useful results only when used with singular values.
* If you use `if` with non-singular values, you will get very wide products which don't seem semantically interesting or useful.
* We acknowledge the semantics gap here, we're still exploring and figuring out the right semantics. Future work! The current semantics, despite being sometimes surprising, are at least well defined and consistent.

Some other interesting examples to try, consider the difference between `e/for` and `let`:
* `(e/for [x (e/amb 1 2)] (prn 1))`
* `(e/for [x (e/amb 1 2)] (prn [x x]))`

Discussion thread: <https://clojurians.slack.com/archives/C7Q9GSHFV/p1732440842517199> (Nov 2024)