# Todos Simple

* minimal todo list. it's multiplayer, try two tabs
* create-new
* optimistic updates
* retry on all states

!fiddle-ns[](electric-tutorial.todos/Todos)

What's happening
* It's a functional todo list, the first "real app"
* <https://github.com/hyperfiddle/electric-starter-app>
* Submit remote txn on enter and clear (e.g. **Chat**, **TodoMVC**, i.e. create new entity). Uncontrolled!

Novel forms
* `ui/checkbox`
* `binding` – reactive dynamic scope; today all Electric defs are dynamic.

Key ideas
* dependency injection
* dynamic scope
* unserializable reference transfer - `d/transact!` returns an unserializable ref which cannot be moved over network, when this happens it is typically unintentional, so instead of crashing we warn and send `nil` instead.
* nested transfers, even inside a loop
* query diffing



# Scratch

* implies optimistic collection maintenance
* failure is routed to the optimistic input for retry, it is not handled here!
* we use dom/On-all because you're editing many entities