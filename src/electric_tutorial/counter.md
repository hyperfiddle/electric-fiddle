# Counter (7 GUIs #1)

It's multiplayer – the counter is on the server. There is progress, and concurrency. Try mashing the button!

!fiddle-ns[](electric-tutorial.counter/Counter)

What's happening

* counter on server
* progress and termination
* event concurrency is handled
* serializable lambda - it's moving from server to client, check the console for `{1 [:electric-tutorial.counter/Counter 1 [] {}]}`

Novel forms

* `dom/OnAll`
* `e/Task`
* `m/sleep`
* `case` being used for imperative sequencing
* `e/snapshot`

Key ideas

*