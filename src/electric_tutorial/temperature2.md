# e/with-cycle — Temperature Converter

* introduce e/with-cycle as a pure functional encoding of state
* introduce e/amb

!fiddle-ns[](electric-tutorial.temperature2/Temperature2)

What's happening


`e/amb` – a key primitive for building UI with Electric

* Hold my beer ... `(e/amb)` holds an ordered collection of values in superposition
* Amb is classically is a multiplexing primitive, representing **concurrent evaluation**
  * as seen in [SICP's amb operator](https://sarabander.github.io/sicp/html/4_002e3.xhtml)
  * also in [Verse](https://simon.peytonjones.org/assets/pdfs/verse-conf.pdf)
* `(e/amb)` means "Nothing", i.e., zero values in superposition
* `(e/amb 1 2)` evaluates to a value representing *both* the `1` and the `2` in superposition, in the same value, at the same time.
  * We call these values `tables`
  * it's what `e/diff-by` returns, and what `e/for` iterates over
  * all electric expressions and scopes (lexical and dynamic) evaluate/resolve to ambs that hold zero or more values in superposition (typically one).
* `(prn (inc (e/amb 1 2)))` will print `2` `3` — the `prn` ran twice, as did the `inc`!
* `(e/for [x (e/amb 1 2)] (prn (inc x))` will also print `2` `3`
* `(e/diff-by identity [1 2])` constructs an amb from a Clojure collection. When the clojure collection evolves over time (e.g. from successive database query results), the key function (e.g. `identity`) is used to stabilize the lifecycle of branches of the amb, which matters if there are effects hooked onto branches via the diff lifecycle, for example to materialize a reactive collection into the DOM.
* `(e/amb 1 2 (e/amb 3) (e/amb))` flattens to `(e/amb 1 2 3)`

Use `e/amb` to accumulate user interaction state and propagate it to the app root via the return path
* `Input` returns its (singular) state in superposition
* On L20, `e/amb` collects values from the two Inputs (which return singular states i.e. `(e/amb 0)` (degrees celcius)) and the two dom/texts (which return Nothing, i.e. `(e/amb)`)
* resulting in `(e/amb (e/amb) (e/amb 0) (e/amb) (e/amb 0))` = `(e/amb 0 0)`, the state of the two inputs, multiplexed
* In effect, collecting *concurrent* states from the DOM and propagating them back up to the app root via return path.
* `(dom/dd)` returns the last child (the macroexpansion puts the children in an implicit `do` - which we may replace with e/amb, to remove boilerplate from e.g. this demo!)
* similarly, `dom/dt` returns it's last child (the text), and `(dom/text "Celcius")` returns `(e/amb)`





FAQ: Why not use `[]` to collect DOM states instead of `e/amb`?
* Because `[]` is not a differential operator, `e/amb` is reactive at the granularity of each individual element.
* e/amb is also syntactically convenient, giving a new ability to return Nothing. Vectors of `nil` do not auto flatten, requiring filtering (an O(n) operation!)

FAQ: Why is `e/amb` built into the language?
* Because the network wire protocol needs to be aware of the diffs, and this impacts the backpressure semantics of the language itself.
* Electric v2's continuous time work skipping semantics were incompatible with diff flows, because dropped values or duplicate values—which are fine in continuous time—are illegal with diff flows. Each diff must be accounted for exactly once.
* Therefore, the Electric v3 computational structure promotes diff flows to a central construct, requiring us to essentially build the differential collection type (e/amb) directly into the language evaluation model, effectively [vectorizing](https://en.wikipedia.org/wiki/Array_programming) the language (c.f. APL, Fortran, MATLAB).
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

~~Mastery of the above (differential e/amb) is necessary in order to fully grok the remaining forms patterns, which are quite sophisticated.~~

# Scratch

* State is handled not with atoms but with a **cycle** in the DAG†. (So if there are cycles is it not a DAG? Well kinda, technically it's still a DAG, we will explain)
* recall `e/with-cycle` from the Temperature Converter tutorial
* Actually let's switch to a simpler imperative implementation (exactly equivalent), which will have familiar control flow:

!fn-src[electric-tutorial.forms2-controlled/Forms2-controlled']()

* `(e/amb (UserForm m) (UserForm m))` is used to render the form twice.
* each form returns its state as a map, so the `e/amb` returns the superposition of the two form states.
