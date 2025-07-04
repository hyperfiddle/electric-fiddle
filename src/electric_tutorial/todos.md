# Todos Simple (WIP) <span id="title-extra"><span>

<div id="nav"></div>

Demonstration of optimistic create-new with failure handling (retry) on all possible states. (Todo - confirm failure is working now and turn back on)

!ns[electric-tutorial.todos/Todos]()

What's happening
* It's a functional todo list, the first "real app"
* `enter` submits a new message in the style of TodoMVC, there is optimistic update so the pending record is immediately seen in the list while the transaction is inflight.
