# Internal tools and Electric Clojure, Part 3

*by Dustin Getz, 2025 Jan 12*

<style>
.Tutorial .user-examples-code { max-height: 50em; }
</style>

This is part 3 of a short series about building internal tools with [Electric Clojure](https://github.com/hyperfiddle/electric).

* In part 1 of this blog series, we built a [crude observability tool in 31 LOC](/blog/y20250106_threaddump_part1). 
* In part 2, we prototyped a [structured data viewer in 99 LOC](/blog/y20250109_datafy), that can browse not just Java threads, but also a **live git repository** via `clojure.datafy`.
* Now, here in part 3, we demonstrate a production-ready **object browser with navigation**, built on Saturday morning (yesterday) using the primitives we built up in parts 1 & 2.

And, here it is! Instructions:
* Select a target object – we give you a java management object, a git repo, and some classes.
* Collections are collapsed under `...`, select the row to navigate and drill down.
* See how deep you can get! Try: `:git > :log > 0 > :id`

<div style="margin-bottom: 2em;"></div>

!target-nochrome[docs-site.blog.threaddump3/ThreadDump3]()

<div style="margin-bottom: 2em;"></div>

Some technical notes: 
* navigation breadcrumbs are stored in the URL
* we consume the URL recursively, to render the stack of table blocks
* each table has virtual scroll, each collection can be large

As before we're interested in:
* How many lines of code?
* How many hours to build?

Why not take a moment to guess?

## Code

* [entity browser code](https://gist.github.com/dustingetz/5dafea5fab3b6480383114b0364f7bbc) as a gist for easy review, I'll let you go check the line count.
* datafy implementations
* app entrypoint

What's changed in the entity browser since Part 2?
* row selection is stored in the URL
* we recursively descend the object, building the blocks for each segment of the URL

Recursive route unrolling is implemented as *actual recursion*:

!fn-src[dustingetz.entity-browser0/BrowsePath]()

* L4 `Block` renders the table at the current level, and returns the current selected row as a value, to be redirected into the URL on L5
* L8: switching to the server to navigate the object *on the server*, the object never hydrates to the client, as it is a *git repository*!!!
* L9: recursion with the child object and remaining paths from the URL
* L3: `router/pop` consumes the route as we descend, so that any relative links are properly interpreted relative to the recursion level

## Time cost

The delta from Part 2 to Part 3 was **4 hours** for the coding bits. I write this now on Sunday after building the app yesterday morning. In those four hours I touched about **50 LOC**:

* clarified the Tree vs Table blocks to appropriately render the focused value at each level,
* used table row selection to drive routes (upgrading the TableScroll component's row selection feature), 
* implemented the recursive route descent,
* fixed a bug in the datafy-git implementation, and
* debugged some CSS.

## Conclusion

In the previous posts, I highlighted how Electric increases **development speed**, and I had planned to write more about that here. But I realized, that merely speed is not the most interesting point. 

What this post actually demonstrates is that Electric has **raised the abstraction ceiling**. I was able to use recursion over the route, to descend a native Java object representing a *git repository*, building up a *stack* of *virtual scroll tables*, that have *intricate client/server topology*, with nuanced frontend and backend processes choreographed in a high performance dance, while retaining the *actual native java object* on the server at all times as I descend (*never escaping* it to the client), *weaving the reference into and out of each table* and the *rows* of each table as you *scroll*! **As an expression! With *functions*!**

This is not mere development velocity. This is an ability to build applications that were **not in reach without this technology,** because how can we even hold their computational structure in our mind with all the incidental framework/architecture *crap* getting in the way? And now, with Electric, I can not only *think* this very interesting computational structure, but I can *actualize it*. It *actually works*!

And even on a Saturday morning, knowing I wanted to ship the final app quickly so I can get on with my weekend, I could not resist the *pure indulgence* of using every bit of abstraction power available to me, in order to make an even better demo than I had originally planned to do. And it's not just a structured thread browser for investigating deadlocks, like I originally intended. It's fully generalized, it works without modification on git repositories, class objects and *literally any other Clojure or Java object* for which you can build a `clojure.datafy` wrapper. AWS S3 browser anyone? [We have that!](https://x.com/dustingetz/status/1873463506861007226)

Consulting plug – DM me `@dustingetz` if you have a commercial use case for this stuff. Remember, this blog series has a time axis, I did all this in a week. **Unleash us on your growth stage business tooling and see what happens: we're going to blow you away.**

Check out [the Electric tutorial here](https://electric.hyperfiddle.net/tutorial/), and [sign up for beta access here](https://www.hyperfiddle.net/early-access.html), we are expanding the beta rapidly.