# FizzBuzz – differential dataflow intro

* Electric v3 implements differential dataflow for UI.
* Goal: start to think about this differential structure.
* Open js console and start incrementing the `10` to see cool diffs

!fiddle-ns[](electric-tutorial.fizzbuzz/FizzBuzz)

What's happening
* as you change the input state, the collection output is incrementally maintained
* L27 `(e/Tap-diffs xs)` is printing diffs to the console as `xs` updates
* The computation is differential - diffs propogating through each expression

`; force lazy let statements otherwise sampled conditionally!`

Diffs!

* `{:degree 12, :permutation {}, :grow 1, :shrink 0, :change {11 "fizz"}, :freeze #{}}`
  * :grow 1, :change {11 "fizz"}

`e/diff-by` inside `RangeN` is the source of diffs
* it returns a reactive collection, kinda
* the reactive collection is represented as a flow of diffs
* the function (from the FRP perspective) is not returning values, it's actually returning a sequence of diffs
* the diffs flow through each expression, incrementally maintaining the computation
* `e/for` is interpreting the diffs (basically looking for `:grow` and `:shrink`) to mount or unmount concurrent branches of the `for`
* this is propagated all the way to the dom
* the reified collection does not exist anywhere in userland, it's diffs all the way to the DOM. The only place you can see the realized collection is in the DOM itself.

Application code is the same as you're used to
* x

Best place to learn more is [Electric Clojure v3: Differential Dataflow for UI (Getz 2024)](https://hyperfiddle-docs.notion.site/Talk-Electric-Clojure-v3-Differential-Dataflow-for-UI-Getz-2024-2e611cebd73f45dc8cc97c499b3aa8b8). This is a good talk, I spent 3 weeks preparing it, please watch it! The next few tutorials will be a bit sparse because they are covered by the talk.

* products
* auto-mapping