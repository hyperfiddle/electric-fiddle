# Input zoo

Four kinds of inputs.

!fiddle-ns[](electric-tutorial.input-zoo/InputZoo)

What's happening


Novel forms


Key ideas


Four kinds of inputs
* Input* -
* Input - local only, no latency, always succeeds, signal of latest value. Use for ephemeral state e.g. a search bar – you always want latest value, there are never conflicts.
* Input! - transactional, models latency/success/failure (i.e. conflict handling) and concurrency. e.g. crud forms bound to stateful entity.
* InputSubmit! - transactional + clears the input when submitted, e.g. a chat box.
