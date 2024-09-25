# InputSubmitCreate! —

Submit remote txn on enter and clear (e.g. **Chat**, **TodoMVC**, i.e. create new entity). Uncontrolled!

!fiddle-ns[](electric-tutorial.forms5/Forms5)

* implies optimistic collection maintenance
* failure is routed to the optimistic input for retry, it is not handled here!
* we use dom/On-all because you're editing many entities

!fn-src[hyperfiddle.input-zoo0/InputSubmitCreate!]()

... !fn-src[hyperfiddle.input-zoo0/CheckboxSubmitClear!]()