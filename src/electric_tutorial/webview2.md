# Webview2

Lambdas

!ns[electric-tutorial.webview2/Webview2]()

What's happening
* Typeahead - we will explain this later, pulling this forward for the cool demo
* sorting
* dynamic columns
* typeahead options support filtering, which happens on the server

Typeahead does IO but encapsulates it!

Performance
* Yes there's some flicker, v3 is slower than v2 right now
* You might assume we're IO bound – we're not actually, this demo is CPU bound
* v3's differential operators are heavy, we need to optimize their usage
* we expect v3's perf to be just as good as v2 generally, and exceed v2 in the collection case due O(1) collection maintenance including O(1) wire traffic to update collections
* optimizations are coming as soon as the language is stable