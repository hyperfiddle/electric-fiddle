# e/amb — concurrent values in superposition

`(e/amb)` is an important primitive in UI and foundational in electric v3, don't skip it!

!fn[electric-tutorial.inputs-local/DemoInputCircuit-amb]()

What's happening
* The two inputs are bound to the same state, but how?
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

Tables are what `e/diff-by` returns, and what `e/for` iterates over.

* `(e/diff-by identity [1 2])` evaluates to `(e/amb 1 2)`
* `(e/for [x (e/amb 1 2)] (prn (inc x))` will print `2` `3`.
  * The `e/for` is isolating the branches of the e/amb so you can think about them one at a time.

Tables are reactive at element granularity, unlike clojure vectors/tuples

* i.e., tables are differential collections
* so, `(println (e/amb 1 (e/watch !b)))` will print twice (`1` and `b`) on initial boot, and subsequently never print `1` again, printing only `b` when it changes
* in other words, `println` will auto-map across the table, and the auto-mapping happens elementwise on *changes*

**Tables are not a data type**, they are built into the language itself as part of it's evaluation model

* In `(let [x (e/amb 1)] ...)`, table `x` is a differential collection of length 1
* In `(let [x 1] ...)` — this expression is identical! lite ral `1` is auto-lifted into a table, and table `x` is a differential collection of length 1.
* Why? Because the network wire protocol needs to be aware of the diffs, and this impacts the backpressure semantics of the language itself.

Why are amb values called "tables" in Electric?

* This is a SQL analogy: most SQL operators, such as `SELECT`, operate on tables (sets of records) not individual records.
* The idea is also reminiscent This is a similar concept as "vector" from [vector programming languages](https://en.wikipedia.org/wiki/Array_programming) such as MATLAB, but (speaking to MATLAB) it is not quite the right concept, as in mathematics, vectors store quantities that have a specific relation between them such that they transform in a specific way under changes in coordinates such as rotations. Electric tables are not this.
* All electric expressions and scopes (lexical and dynamic) evaluate/resolve to tables that hold zero or more values in superposition (typically one).

How is the table `(e/amb 1 2)` different from the Clojure vector `[1 2]`?

* Electric tables are incrementally maintained data structures and are reactive at the granularity of the individual element.
* I.e., Electric tables represent concurrent processes that produce updated states independently.
  * e.g. `(prn (e/amb 42 (e/System-time-ms)))`
* so we want to ensure incremental updates, and only compute `prn` on the **changeset**
* `(e/as-vec (e/amb 1 2))` returns `[1 2]` - **materializing** the Clojure vector from the incremental Electric table (by reducing over the flow of diffs!)

The above is enough to understand <https://electric.hyperfiddle.net/electric-tutorial.form-explainer!FormExplainer/> where we use `e/amb` to collect concurrent form edit commands from the user.

### SICP connection

* Amb is classically is a multiplexing primitive, representing **concurrent evaluation**
* as seen in [SICP's amb operator](https://sarabander.github.io/sicp/html/4_002e3.xhtml) and also [Verse](https://simon.peytonjones.org/assets/pdfs/verse-conf.pdf)


> SICP: The key idea here is that expressions in a nondeterministic language can have more than one possible value. ... Abstractly, we can imagine that evaluating an amb expression causes time to split into branches, where the computation continues on each branch with one of the possible values of the expression.

Yes, `e/amb` models "nondeterminism" like in SICP, in the sense that `(inc (e/amb 1 2))` evaluates to more than one result:

```
; SICP 4.3.1
(list (e/amb 1 2 3) (e/amb :a :b))
; (1 :a) (1 :b) (2 :a) (2 :b) (3 :a) (3 :b)
```

I don't like the word "nondeterminism" though, it doesn't mean the thing that most programmers will assume it means if they aren't familiar with FP literature.

If you try to run this example, note that because of how electric-dom works, you'll need to materialize with `e/as-vec` to see it:
```
(dom/p (dom/text (pr-str (e/as-vec (list (e/amb 1 2 3) (e/amb :a :b))))))
; "[(1 :a) (1 :b) (2 :a) (2 :b) (3 :a) (3 :b)]", in the DOM
```
Without `e/as-vec`, you'll get 6 concurrent point writes to the same dom textnode, i.e. the writes will race and you'll see only the last one.

### Advanced

Here is SICP's `prime-sum-pair`, which actually demonstrates an issue with Electric's if semantics (backlog):

```
(e/defn Prime-sum-pair [as bs]
  (e/for [a as, b bs] ; why the e/for, why not operate on tables directly?
    (if (prime? (+ a b))
      [a b]
      (e/amb))))

(Prime-sum-pair
  (e/amb 1 3 5 8)
  (e/amb 20 35 110))

; [3 20] [3 110] [8 35]
```
The problem here is that we want to write it as simply
```
(e/defn Prime-sum-pair [as bs]
  (if (prime? (+ as bs))
    [as bs]
    (e/amb)))
```
where `+`, `prime?`, `if` etc are auto-mapped. The problem is that our `if` primitive does not perform *narrowing*. We wish the truthy branch to only see values of `as` and `bs` that passed the predicate, but we did not implement that yet. So, we workaround by using `e/for` to narrow each table down to a singular value before calling into `clojure.core/if`. Future work: implement `if` narrowing and bring inline with Verse/SICP semantics!

(For this reason, I recommend you mostly ignore the auto-mapping functionality of Electric, just use `e/for` to iterate over tables as singular values, and your if statements will be happy.)
