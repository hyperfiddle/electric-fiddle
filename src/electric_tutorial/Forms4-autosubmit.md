# Transactional CRUD *fields* that autosubmit (just remove the stage)

* Input! without any stage - auto-submit or auto-save.
* unbuffered, so add a stage if you want to buffer (see prev tutorial)

!fiddle-ns[](electric-tutorial.forms4-autosubmit/Forms4-autosubmit)

What's happening
* auto-save behavior: doesn't wait for enter, as soon as your dirty, it submits the commit
* busy state - yellow
* failure state - red (at field level now!)
* cancel? no
* retry? no
* If you have auto-save, it should always succeed - a google search never fails
* (What happens if you twice toggle a checkbox? Given 500ms delay, the first should cancel, the second should fail - we have an issue here)

Why / what for
* Submit a field immediately on interaction - like iphone settings, todomvc diagnostics, b2b saas settings. (Don't need commit/discard, they auto-save)
* Can this subsume the simple controlled `Input` usecase (i.e. fizzbuzz)? Yes, but the API is more complicated. The equivalent form would be for the caller to synchronously burn the token in the same propagation cycle as its creation, which is pretty dumb. TodoMVC uses InputEager! for compatibility with the state command pattern used everywhere else in the app
* Typeahead, or google search - it's running the query eagerly, as you type, to show results sooner. That means, we need the token to open a dirty state and show spinner. As we continue to type, the same token is reused, superseded in flight queries are cancelled and new queries are created with updated values.
  * missionary will cancel the old query since an argument changed. if the query is running in a thread via m/via, missionary will raise ThreadInterruptException. Your data layer must respect this (Datomic doesn't, for example).

* Tutorial usage: <a href="http://localhost:8080/(electric-tutorial.tutorial!%54utorial)/(electric-tutorial.todomvc!%54odo%4D%56%43)">TodoMVC diagnostics</a>

How it works
* (does NOT have an inline stage, does NOT implement discard/cancel - consider CheckboxSubmit! vs Checkbox! a CheckboxSubmit! can cancel.)

User now asks - what about retry on that failure? Onwards ...