# Dir Tree <span id="title-extra"><span>

<div id="nav"></div>

* Recursive traversal over directory structure on the server, with in-flight rendering to a recursive DOM view on the client, all in one function.
* This is an example of something that is easy to do in Electric but hard to do with competing technologies.
* Goal: get comfortable with Electric v3's client/server transfer semantics in a more interesting topology.

!ns[electric-tutorial.dir-tree/DirTree]()

What's happening

* client-side rendering the contents of the `src/hyperfiddle` directory (a server resource)
* client-side filtering through the dom/input - try it
* File system I/O is done with standard Java operators
* `Dir-tree*` is a **single-pass recursive function**, it both traverses the directory tree and renders it to the client in a single pass
* The view code deeply nests client and server calls, arbitrarily, even through loops and recursion. The control flow switches between client and server as if it didn't matter – because it doesn't! The Electric compiler figures it out.
* How would you even approach this with GraphQL, REST, or some other dataloader abstraction?

