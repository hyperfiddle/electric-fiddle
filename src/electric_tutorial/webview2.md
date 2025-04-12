# Webview2 <span id="title-extra"><span>

<div id="nav"></div>

demonstration of Electric Lambda

!ns[electric-tutorial.webview2/Webview2]()

What's happening
* Typeahead - we will explain this later, pulling this forward for the cool demo
* sorting (REPL only - see talk)
* dynamic columns (REPL only - see talk)
* typeahead options support filtering, which happens on the **server** (i.e., support for large collections)

**24 LOC** typeahead implementation
* Note: this is not a production-grade typeahead, this is a tutorial demo

!fn-src[electric-tutorial.typeahead/Typeahead]()

Here is the datascript model, if you're coding along:

!ns-src[dustingetz.teeshirt-orders-datascript]()