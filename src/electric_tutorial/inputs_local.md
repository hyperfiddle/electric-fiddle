# Inputs <span id="title-extra"><span>

<div id="nav"></div>

Electric provides two input patterns – **synchronous local inputs** and **async transactional inputs**, which satisfy two different use cases. Here we discuss the former, sync local inputs.

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

