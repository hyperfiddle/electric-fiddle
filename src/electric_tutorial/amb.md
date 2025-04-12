# e/amb — concurrent values in superposition <span id="title-extra"><span>

<div id="nav"></div>

* Amb is classically is a multiplexing primitive, representing **concurrent evaluation**, as seen in [SICP's amb operator](https://sarabander.github.io/sicp/html/4_002e3.xhtml) and also [Verse](https://simon.peytonjones.org/assets/pdfs/verse-conf.pdf).
* We've found that in UI, `(e/amb)` is an important primitive for representing concurrent reactive processes, and it is foundational in Electric v3.
* If you understand most of this page, you'll know enough to understand the <a href="/tutorial/form_explainer">form tutorial</a> where we use `e/amb` to collect concurrent form edit commands from the user.

!fn[electric-tutorial.inputs-local/DemoInputCircuit-amb]()

What's happening
* The two inputs are bound to the same state `s`, but how?
* `s'` is a "table" (i.e., ambiguous "multi-value").
* This table `s'` contains two values in superposition!, almost like a tuple
* There is only one `reset!`, `(reset! !s s')`, which is run when *either* or *any* of the values in table `s'` change!
