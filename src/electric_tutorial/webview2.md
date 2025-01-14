# Webview2 <span id="title-extra"><span>

<div id="nav"></div>

demonstration of Electric Lambda

!ns[electric-tutorial.webview2/Webview2]()

What's happening
* Typeahead - we will explain this later, pulling this forward for the cool demo
* sorting (REPL only - see talk)
* dynamic columns (REPL only - see talk)
* typeahead options support filtering, which happens on the **server** (i.e., support for large collections)

Discussion of IO patterns
* This was the topic of [Talk: Electric Clojure v3: Differential Dataflow for UI (Getz 2024)](https://hyperfiddle-docs.notion.site/Talk-Electric-Clojure-v3-Differential-Dataflow-for-UI-Getz-2024-2e611cebd73f45dc8cc97c499b3aa8b8), now is a good time to watch it before moving on to the next demo, which is a followup from the talk.
* Typeahead does IO but encapsulates it!

**24 LOC** typeahead implementation
* Note: this is not a production-grade typeahead, this is a tutorial demo. We have better ones now

!fn-src[electric-tutorial.typeahead/Typeahead]()

Performance notes (updated 2024 Dec 22)
* ~~Yes there's some flicker, v3 is slower than v2 right now~~
  * flicker is fixed, remaining jank is layout shift due to lazy css coding, todo improve css
* ~~You might assume we're IO bound – we're not actually, this demo is CPU bound~~
  * flicker is fixed

Here is the datascript model, if you're coding along:

!ns-src[dustingetz.teeshirt-orders-datascript]()