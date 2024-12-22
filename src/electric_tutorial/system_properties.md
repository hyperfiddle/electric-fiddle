# System Properties <span id="title-extra"><span>

<div id="nav"></div>

* A larger example of a HTML table backed by a server-side query.
* Type into the input and see the query update live.
* Goal here is to understand basic client/server data flow in a simple query/view topology.

!ns[electric-tutorial.system-properties/SystemProperties]()

What's happening

* There's a HTML table on the frontend, backed by a backend "query" `jvm-system-properties`
* The backend query is an ordinary Clojure function that only exists on the server, which works because this is an ordinary `.cljc` file.
* Typing into the frontend input causes the backend query to rerun and update the table.
* There's a reactive for loop to render the table - `e/for`
* The view code deeply nests client and server calls, arbitrarily, even through loops.

Ordinary Clojure/Script functions work
* `clojure.core/defn` works as it does in Clojure/Script, it's still a normal blocking function and is opaque to Electric. Electric does not mess with the `clojure.core/defn` macro.
* **query can be any function**: return collections, SQL resultsets, whatever

direct query/view composition
* `jvm-system-properties`, a server function, composes directly with the frontend DOM table.
* Thus unifying your code into one paradigm, promoting readability, and making it easier to craft complex interactions between client and server components, maintain and refactor them.

e/for, e/diff-by

* The table rows are renderered by a for loop. Reactive loops are efficient and recompute branches only precisely when needed.
* `e/for`: a reactive map operator that works on electric v3's "reactive collections" (kinda, we will sharpen this later).
* `(e/diff-by key system-props)` constructs a "reactive collection" from a regular Clojure collection. `key` (c.f. [React.js key](https://stackoverflow.com/questions/28329382/understanding-unique-keys-for-array-children-in-react-js/43892905#43892905)) is a function that identifies a stable entity in a collection as it evolves over time as the query results update. Collection elements with the same identity will be reconciled and reuse the same location in the reactive collection, which here is mirrored to the DOM.
* Note, the pattern `(e/for [[k v] (e/server (e/diff-by key system-props))] ...)` implies collection diffs on the wire! When the query result changes, only the *differences* are moved, not the *entire collection*.

Simple free text input
* **callback free**: `(dom/On "input" #(-> % .-target .-value) ""))` returns the current value of the input as a reactive value (i.e., a *signal* from the FRP perspective). `""` is the initial state of the signal.
* the clojure lambda is an extractor function which needs to be written in Clojure not Electric because of the DOM's OOP semantics. If you wrote it like `(-> (dom/On "input" identity "") .-target .-value)`, because the `.-target` reference is the same with each event, `(.-value target)` will work skip.
* cycle by side effect - we're using an atom to loop the input value higher in lexical scope. Super common idiom, more on this later.
* **Note we're cycling values directly – there are no callbacks!**
* `e/watch`: derives a reactive flow from a Clojure atom by watching for changes using the `clojure.core/add-watch` subscription API.

Reactive for details

* `e/for` ensures that each table row is bound to a logical element of the collection, and only touched when a row dependency changes.
* Notice there is a `println` inside the for loop. This is so you can verify in the browser console that it only runs when its arguments change. Open the browser console now and confirm for yourself:
  * On initial render, each row is rendered once
  * Slowly input "java.class.path"
  * As you narrow the filter, no rows are recomputed. (The existing dom is reused, so there is nothing to recompute because, for those rows, neither `k` nor `v` have changed.)
  * Slowly backspace, one char at a time
  * As you widen the filter, rows are computed as they come back. That's because they were unmounted and discarded!
  * Quiz: Try setting an inline style "background-color: red" on element "java.class.path". When is the style retained? When is the style lost? Why?

Network transfer can be reasoned about clearly

* values are only transferred between sites when and if they are used. The `system-props` collection is never actually accessed from a client region and therefore never escapes the server.
* Look at which remote scope values are closed over and accessed.
* Only remote access is transferred. Mere *availability* in scope does not transfer.
* In the `e/for`, `k` and `v` exist in a server scope, and yet are accessed from a client scope.
* Electric tracks this and sends a stream of individual `k` and `v` updates over network.
* The collection value `system-props` is not accessed from client scope, so Electric will not move it. Values are only moved if they are accessed.

FAQ: "It seems scary that Electric blurs the lines between client and server?"
* I reject "blurs the line", in our opinion the boundary is *precise and clear*
* In fact, Electric v3 allows the programmer to draw much more intricate boundaries than ever before
* **Electric is a surgical tool offering *tremendous precision* in how we specify network state distribution - it is not "blurry"**

Network transparent composition is not the heavy, leaky abstraction you might think it is

* The DAG representation of the program makes this simple to do
* The electric core implementation is about 3000 LOC
* Function composition laws are followed, Electric functions are truly functions.
  * Functions are an abstract mathematical object
  * Javascript already generalizes from function -> async function (`async/await`) -> generator function (`fn*/yield`)
  * Electric generalizes further: stream function -> reactive function -> distributed function
* With Electric, you can refactor across the frontend/backend boundary, all in one place, without caring about any plumbing.