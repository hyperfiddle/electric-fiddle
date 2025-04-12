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
