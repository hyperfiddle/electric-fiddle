# Inputs <span id="title-extra"><span>

<div id="nav"></div>

* Before we discuss forms, we first need to understand Inputs.
* Electric provides two input patterns – **synchronous local inputs** and **async transactional inputs**, which satisfy two different use cases.
* Here we discuss the former, sync local inputs.

### naive low-level DOM input – uncontrolled state

Warmup and for reference, the low-level input that you're already familiar with.

!fn[electric-tutorial.inputs-local/DemoInputNaive]()

* dom/on - derive a state (latest value, or signal from the FRP perspective) from a sequence of events
* recall that dom elements return their last child via implicit do

### synchronous `Input` - uncontrolled state

* Ephemeral local state
* Very simple
* No error states, no latency

!fn[electric-tutorial.inputs-local/DemoInputCircuit-uncontrolled]()

### synchronous `Input` – controlled state

!fn[electric-tutorial.inputs-local/DemoInputCircuit-controlled]()

* Observe two `reset!` effects

### synchronous forms

!fn[electric-tutorial.inputs-local/DemoFormSync]()

* Why are they ordered? I think we're getting lucky with the reader (todo explain)
* It's just an educational demo, vector them if you want
* One problem here is the clojure data structure (map, vector etc) will destroy fine grained reactivity at element level, propagating form change batches from that point rather than individual field changes even when only one field changes. We're about to do better.