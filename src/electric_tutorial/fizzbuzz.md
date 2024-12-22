# Differential FizzBuzz <span id="title-extra"><span>

<div id="nav"></div>

* Electric v3 implements differential dataflow for UI.
* Goal: start to think about this differential structure.
* Open js console and start incrementing the `10` to see cool diffs

!ns[electric-tutorial.fizzbuzz/FizzBuzz]()

What's happening
* as you change the input state, the collection output is incrementally maintained
* L27 `(e/Tap-diffs xs)` is printing diffs to the console as `xs` updates
* The computation is differential - diffs propogating through each expression

Diffs!

* `{:degree 12, :permutation {}, :grow 1, :shrink 0, :change {11 "fizz"}, :freeze #{}}`
  * `:grow 1` - incremental instruction to grow the collection by 1 element
  * `:change {11 "fizz"}` - incremental instruction to set the value of element at an index in the collection

`e/diff-by` inside `RangeN` is the source of these diffs
* `diff-by`'s return value is a reactive collection, kinda. We will explain later, for now let's just pretend there is a reactive collection type.
* The reactive collection is represented as a flow of diffs.
* The `RangeN` electric function, from the FRP perspective, is not returning values, it's actually returning a sequence of diffs!
* The diffs flow through each expression in the Electric DAG, incrementally maintaining the computation as it goes.
* `e/for` is interpreting the diffs (basically looking for `:grow` and `:shrink`) to **mount** or **unmount** concurrent branches of the `for` body
* Yes, this is analogous to the React lifecyle methods, except mount/unmount is not collection aware but grow/shrink is!
* These grow/shrink instructions are propagated all the way to the DOM.
* The reified collection does not exist anywhere in userland, it's diffs all the way to the DOM. The only place you can see the realized collection is in the DOM itself.

Application code is the same as you're used to
* The differential structures are internal, userland never sees them unless you use Tap-diff, which is implemented with special forms.

`;force lazy let statements otherwise sampled conditionally!`
* Electric `let` bindings are evaluated lazily - when sampled
* here, bindings `fizz` and `buzz` are sampled conditionally - see both `cond` and `e/for`
* That means their side effects (i.e., writing to the DOM) might not happen
* Touch them inside an implicit `do` to force these effects – the `do` is sampled unconditionally

Best place to learn more is [Electric Clojure v3: Differential Dataflow for UI (Getz 2024)](https://hyperfiddle-docs.notion.site/Talk-Electric-Clojure-v3-Differential-Dataflow-for-UI-Getz-2024-2e611cebd73f45dc8cc97c499b3aa8b8). This is a good talk, I spent 3 weeks preparing it, please watch it! The next few tutorials will be a bit sparse because they are covered by the talk.