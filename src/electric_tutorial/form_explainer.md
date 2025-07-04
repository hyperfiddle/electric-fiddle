# Transactional server forms <span id="title-extra"><span>

<div id="nav"></div>

* server commands / transactions / RPC (i.e., "http post")
* forms with dirty and error states with latency and failure affordances

!ns[electric-tutorial.form-service/FormsService]()

What's happening

* transactional/RPC form post, with commit/discard buttons
* controlled state bound to database record
* dirty fields turn yellow, discard to reset
* uncommitted state is *staged*, you can continue to arrange it until you submit
* on submit, commit button turns yellow while you wait
* inflight commit can be cancelled - try quickly cancelling an inflight txn before it succeeds (there is a 500ms delay)
* failed commits turn commit button red, retaining dirty state (i.e., **uncommitted state is never lost**)
* failed commits can be retried

<!-- !fn-src[electric-tutorial.form-service/UserFormServer]() -->

<!-- !fn-src[electric-tutorial.form-service/User-form-submit]() -->

<!-- !fn-src[electric-tutorial.form-service/UserService]() -->
