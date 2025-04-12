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

