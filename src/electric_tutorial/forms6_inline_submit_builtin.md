# InputSubmit! —

inline remote txn

Submit remote CRUD update on enter, no clear (e.g. **TodoMVC**, spreadsheet cells)

!fiddle-ns[](electric-tutorial.forms6-inline-submit-builtin/Forms6-inline-submit-builtin)

* inline commit/discard (it can be integrated w/ the controls)
* implies that the control disables while committing, you must cancel to edit (it can auto-cancel)
* retry and resubmit
* means the impl must use dom/on, not dom/on-all - user is done here unless they cancel!

!fn-src[hyperfiddle.input-zoo0/InputSubmit!]()

doesn't exist - !fn-src[hyperfiddle.input-zoo0/CheckboxSubmit!]()