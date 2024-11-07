# Todos Simple (WIP)

* Point of this demo is to demonstrate optimistic create-new with failure handling (retry) on all possible states. If you don't care about failure handling, then there is a much better implementation possible (without nesting a form).
* Unfortunately, we didn't finish implementing the failure states yet (as of October 20 2024).
* I think it otherwise works

!ns[electric-tutorial.todos/Todos]()

What's happening
* It's a functional todo list, the first "real app"
* <https://github.com/hyperfiddle/electric-starter-app>
* Submit remote txn on enter and clear (e.g. **Chat**, **TodoMVC**, i.e. create new entity). Uncontrolled!

Create new form - auto-submit false, genesis true
  input
Item form - auto-submit true, this has the retry state
  Item status form - auto-submit true
    checkbox
  Item desc form - auto-submit false
    input


Key ideas
* dependency injection
* dynamic scope
* unserializable reference transfer - `d/transact!` returns an unserializable ref which cannot be moved over network, when this happens it is typically unintentional, so instead of crashing we warn and send `nil` instead.
* nested transfers, even inside a loop
* query diffing


# Scratch

* implies optimistic collection maintenance
* failure is routed to the optimistic input for retry, it is not handled here!