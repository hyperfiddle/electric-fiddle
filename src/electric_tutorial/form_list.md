# Form composition <span id="title-extra"><span>

<div id="nav"></div>

* very simple demo showing forms composing as values in a list

!ns[electric-tutorial.form-list/FormList]()

What's happening
* now it's a list of forms, bound to the same server entity
* the two forms are sent to the server concurrently
* server states, dirty/failure/retry etc all work