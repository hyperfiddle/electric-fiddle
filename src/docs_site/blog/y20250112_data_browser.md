# Object Browser demo — Internal Tools with Electric Clojure, Part 3

*by Dustin Getz, 2025 Jan 29*

<style>
.Tutorial .user-examples-code { max-height: 50em; }
</style>

This is part 3 of a short series about building internal tools with [Electric Clojure](https://github.com/hyperfiddle/electric).

* In part 1 of this blog series, we built a [crude observability tool in 31 LOC](/blog/y20250106_threaddump_part1). 
* In part 2, we prototyped a [structured data viewer in 99 LOC](/blog/y20250109_datafy), that can browse not just Java threads, but also a **live git repository** via `clojure.datafy`.
* Now, here in part 3, we demonstrate a production-ready **object browser with navigation**, built on Saturday morning (yesterday) using the primitives we built up in parts 1 & 2.

And, here it is! Instructions:
* Demo is hardcoded to target a git repository object, courtesy of JGit
* Table rows are selectable, click any row to select it and if it is navigable it will drill down.
* Play around, explore, and see how deep you can get! Some paths to try:
  * `:log 0 :id`
  * `:repo :dir :parent :children` oh wait is that the source directory? :)
* Mobile notes: The CSS is a bit wonky on mobile - try landscape mode. The fact that it works on mobile at all, I personally think is impressive.
* [Fullscreen mode here](/docs-site.blog.threaddump3!ThreadDump3/)

<div style="margin-bottom: 2em;"></div>

!target-nochrome[docs-site.blog.threaddump3/ThreadDump3]()

<div style="margin-bottom: 2em;"></div>

As before we're interested in:
* How many lines of code?
* How many hours to build?

Why not take a moment to guess?

## Code

Here is the [code as a gist](https://gist.github.com/dustingetz/5dafea5fab3b6480383114b0364f7bbc) for easy review, I'll let you go check the line count. I've included there both the entity browser, and the datafy-git impl which has been upgraded.

Lets take a quick look, the most interesting thing here, IMO, is that we're **storing a stack of breadcrumbs in the URL and using *actual recursion* to traverse it**:

!fn-src[dustingetz.entity-browser0/BrowsePath]()

Lets focus on the recursion (the recursive call is on L8).

* `Block` renders the table picker at the current level ("locus" means - focused place)
* `when-some` is the recursion guard, the stack we're traversing is stored in the URL
* `router/pop` makes it so as we descend the stack, each `Block` can think about hyperlinks/navigation relative to its current place in the stack, without knowing how deep it is in the stack
* As we descend, on L7, we maintain the java object at that locus *on the server*, right in the middle of the recursion! No problem!
* The hack on L6 is basically the same as using a React.js key to force a component to be rebuilt if its key changes. Here I'm doing it to force each table to be rebuilt (and reload network) if the underlying target object changes. This made a consistency glitch go away, todo investigate.

There are many more interesting things to discuss in this demo but this is probably not the right medium - perhaps a talk. The big insight is that, with Electric, **we can now use elementary algorithms & data structures techniques such as recursion and stacks, to model and build richly interactive UIs, for pretty much the first time ever**. What other technology can express this recursive structure?

## Time cost to build this

The delta from Part 2 to Part 3 was **4 hours** for the coding bits, which I did on Saturday morning, Jan 11. In those four hours I touched about **50 LOC**:

* clarified the Tree vs Table blocks to appropriately render the focused value at each level,
* used table row selection to drive routes (upgrading the TableScroll component's row selection feature), 
* implemented the recursive route descent,
* fixed a bug in the datafy-git implementation, and
* debugged some CSS.

(Editor's note: timing wise, we delayed publication of this demo by a few weeks in order to fix an Electric bug that was causing a crash. The demo was feature-complete on Jan 11.)

## Raising the abstraction ceiling in web development

In the previous posts, I highlighted how Electric increases **development speed**, and I had planned to write more about that here. But I realized, that merely speed is not the most interesting point. 

What this post actually demonstrates is that Electric has **raised the abstraction ceiling**. I was able to use recursion over the route, to descend a native Java object representing a *git repository*, building up a *stack* of *virtual scroll tables*, that have *intricate client/server topology*, with nuanced frontend and backend processes choreographed in a high performance dance, while retaining the *actual native java object* on the server at all times as I descend—it never escapes to the client—*weaving the reference into and out of each table* and the *rows* of each table as you *scroll*! **As an expression! With *functions*!**

This is not mere development velocity. This is an ability to now build applications that were **not in reach before** because how can we even hold their computational structure in our mind with all the incidental framework/architecture *crap* getting in the way? And now, with Electric, I can not only *think* this recursive computational structure interweaving client and server, but I can *actualize it*. It *works*! And it's fully generalized!, thanks to `clojure.datafy`! AWS S3 browser anyone? [We have that!](https://x.com/dustingetz/status/1873463506861007226) Or, how about [the world's worst Hacker News client](https://x.com/dustingetz/status/1880662258457665555), resolving content from CDN as you scroll! Or, should we build an [actual git client](https://x.com/dustingetz/status/1879903815648080099)? Maybe a [git branch deleter tool](https://x.com/dustingetz/status/1869840759949799512)? How about [clojure.tap](https://x.com/dustingetz/status/1873119581419782225)?

## What's Next

For the next month, we're going to see how far we can push these ideas. We'll add editable forms, type metadata reflection, column configurations, better data sources (such as JDBC), and see where it takes us. 

Consulting plug – DM me `@dustingetz` if you have a commercial use case for observability tools like this. Remember, this blog series has a time axis: I built this datafy browser in a week. **Unleash us on your growth stage business tooling and see what happens: we're going to blow you away.**