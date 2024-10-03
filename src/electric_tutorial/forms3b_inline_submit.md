# Transactional fields with inline submit

* auto-save true : Submit a field immediately on interaction - like iphone settings, todomvc diagnostics, b2b saas settings. (Don't need commit/discard, they auto-save)
* Use cases: TodoMVC, consumer apps - field submits should always succeed, error likely means network partition or server down
* inline remote txn with auto submit, like in most "apps" (not "forms")

!ns[](electric-tutorial.forms3b-inline-submit/Forms3b-inline-submit)

What's happening
* Each field is a form, each gets its own commit/discard
* Keyboard support - enter to commit, esc to discard
* The buttons are optional - toggle them off! The only reason to have them visible here is to document the semantics.
* `:commit`
* cancel - yes, even on auto-submit, even when buttons are hidden!
* retry - yes, even on auto-submit, even when buttons are hidden!
* retry state is on the form (commit button), not the field. field stays yellow

Configurations
* auto-submit
* show-buttons

Use cases
* Tutorial usage: <a href="http://localhost:8080/(electric-tutorial.tutorial!%54utorial)/(electric-tutorial.todomvc!%54odo%4D%56%43)">TodoMVC diagnostics</a>
* Spreadsheet - inline commit/discard
* TodoMVC - inline commit/discard with failure and retry (with optimistic updates)
* Tutorial usage: <a href="">Chat</a>, <a href="">ChatMonitor</a>, <a href="">Todos</a>, <a href="">TodoMVC</a>
* Submit remote CRUD update on enter, no clear (e.g. **TodoMVC**, spreadsheet cells)
* Apps - TodoMVC - spam toggling should cancel in flight and auto submit, and there is an enter interaction on both the edit and the create-new


What it's for

* Typeahead, or google search - it's running the query eagerly, as you type, to show results sooner. That means, we need the token to open a dirty state and show spinner. As we continue to type, the same token is reused, superseded in flight queries are cancelled and new queries are created with updated values.
  * missionary will cancel the old query since an argument changed. if the query is running in a thread via m/via, missionary will raise ThreadInterruptException. Your data layer must respect this (Datomic doesn't, for example).

what it's NOT for
* Fizzbuzz circuit inputs - circuits are a simpler API, but do not have transactional affordance (commit, cancel, retry).




How it works
* Each field gets its own `Form`! `(Form (Field id :user/str1 (Input! str1)))`
* pretty cool right? Just put the form where it makes sense for the submit granularity you desire. (By the way, it composes, so you can layer them!)

Why wrap fields in a form?
* :commit - you need to translate the control value to an effect
* the submit semantics come from the form - the field dirties as you type, when you press enter, HTML semantics submit the form it's contained in!


; concurrent field edits (which get us field dirty state).
; if we used e.g. a vector/map aggregator like before, we'd need
; circuit controls and therefore lose field dirty state.

auto-save
* doesn't wait for enter, as soon as your dirty, it submits the commit
* probably doesn't make sense for free text inputs, but makes sense on checkboxes, picklists, radios, ...


* Can this subsume the simple controlled `Input` usecase (i.e. fizzbuzz)? Yes, but the API is more complicated. The equivalent form would be for the caller to synchronously burn the token in the same propagation cycle as its creation, which is pretty dumb.


* If you have auto-save, it should always succeed - a google search never fails
* (What happens if you twice toggle a checkbox? Given 500ms delay, the first should cancel, the second should fail - we have an issue here)




* Matches the true semantics of <a href="">TodoMVC</a> and a spreadsheet (tab - commit; esc - discard), but these controls have a poor UX. Onward!