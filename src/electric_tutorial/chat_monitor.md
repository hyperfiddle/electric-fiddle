# Chat Monitor

Pending

!fiddle-ns[](electric-tutorial.chat-monitor/ChatMonitor)

`(e/with-cycle* first [edits (e/amb)] ...)`
* note the `*` and the `first`, this is not `e/with-cycle`
* this is a temporary hack
* we need a cycle primitive that cycles tables, not just clojure values

Optimistic updates
* `!` inputs return requested next state as an `edit` i.e. `[t v]`
* Loop the edits locally
* edits fed into query
* query interprets edits, merging them into the previous collection value
* Edit eventually succeeds, txn is cleared `(t)`, pending state falls out of query
* Nice!

Let's formalize this pattern!