


FAQ: Why is `e/amb` built into the language?

* Because the network wire protocol needs to be aware of the diffs, and this impacts the backpressure semantics of the language itself.
* Electric v2's continuous time work skipping semantics were incompatible with diff flows, because dropped values or duplicate values—which are fine in continuous time—are illegal with diff flows. Each diff must be accounted for exactly once.
* Therefore, the Electric v3 computational structure promotes diff flows to a central construct, requiring us to essentially build the differential collection type (e/amb) directly into the language evaluation model, effectively vectorizing the language.
* This is discussed in [Electric Clojure v3: Differential Dataflow for UI (Getz 2024)](https://hyperfiddle-docs.notion.site/Talk-Electric-Clojure-v3-Differential-Dataflow-for-UI-Getz-2024-2e611cebd73f45dc8cc97c499b3aa8b8).



Differential `e/amb` (this is important)

* Note that `(reset! !m (e/amb a b))` is a differential product. What's happening? Why does it work?
* Recall that e/amb superposition is *reactive* and *differential* – that means, only *changes* propagate.
* `reset!` will auto-map across the table, and the auto-mapping happens elementwise on *changes*
* if `a` and `b` are equationally bound to the same state (i.e. the same electrical circuit), when one changes, the other follows, you'll get a second `reset!` but the second `reset!` will work-skip, the computation reaches a fixed point and halts.


Diff-by

* `(e/diff-by identity [1 2])` constructs an Electric table from a Clojure collection. When the clojure collection evolves over time (e.g. from successive database query results), the key function (e.g. `identity`) is used to stabilize the lifecycle of branches of the amb, which matters if there are effects hooked onto branches via the diff lifecycle, for example to materialize a reactive collection into the DOM.