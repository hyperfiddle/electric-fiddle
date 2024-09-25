# Forms6-inline-submit with proper "spreadsheet" UX

`InputSubmit!` & `InputSubmitCreate!` - the holy grail, but hard to implement - wip.

inline remote txn with auto submit, like in most "apps" (not "forms")

Use cases
* CRUD SPreadsheet
* Apps - TodoMVC - spam toggling should cancel in flight and auto submit, and there is an enter interaction on both the edit and the create-new

Submit remote CRUD update on enter, no clear (e.g. **TodoMVC**, spreadsheet cells)

!fiddle-ns[](electric-tutorial.forms6-inline-submit-builtin/Forms6-inline-submit-builtin)

What's happening

* including create-new with retry (the hard case)
* Auto-commit fields -
  - todo affordance for create/discard inline (no buttons)
    - possible debug mode with inline buttons/stage?
* optimistic list updates
* pending, ok, fail, retry, including optimistic create new with retry (the hard case)
* inflight cancellation
* layered stages ?

Why / what for

* Spreadsheet - inline commit/discard
* TodoMVC - inline commit/discard with failure and retry (with optimistic updates)
* Tutorial usage: <a href="">Chat</a>, <a href="">ChatMonitor</a>, <a href="">Todos</a>, <a href="">TodoMVC</a>

How it works
* buffered is how you fix this
  - buffer is achieved by proxy tokens on commit/discard.
  - the proxy retains the original token in memory until you commit it, that's the buffer.




# Scratch

* inline commit/discard (it can be integrated w/ the controls)
* implies that the control disables while committing, you must cancel to edit (it can auto-cancel)
* retry and resubmit
* means the impl must use dom/on, not dom/on-all - user is done here unless they cancel!

!fn-src[hyperfiddle.input-zoo0/InputSubmit!]()

!fn-src[hyperfiddle.input-zoo0/CheckboxSubmit!]()