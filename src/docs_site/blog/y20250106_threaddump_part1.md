# Internal tools and Electric Clojure, Part 1 <span id="title-extra"><span>

*by Dustin Getz, 2025 Jan 7*

<style>
.Tutorial .user-examples-code { max-height: 25em; }
.Tutorial > .ThreadDump { position: unset; }
.Tutorial > .ThreadDump > fieldset { height: 25em; position: relative; }
</style>

So, this week at Hyperfiddle HQ, we're stress testing Electric Clojure v3, and under load we observe what looks like an **async clock deadlock in prod**. Leo, our resident concurrency expert, says to try to get the thread dump if possible. 

**"Try if possible"**, because, this is nontrivial to do actually! VisualVM, the debugger tool you need to do this, is a Java native desktop app and therefore doesn't run in prod. I could try to get a socket to prod (for VisualVM or even a Clojure REPL), but we run a multi-region cloud deployment with load balancers and stuff and even if I can manage to get a socket to the actual machine, now I'm gonna be evaluating random code in prod, and it just seems annoying and hard and really I want to just give Leo a link so he can figure it out himself without replicating all this *crap*, and—oh is that a slack message? because I'd definitely rather be hanging out on slack then wasting another day debugging the cloud.

Except, I have [Electric Clojure](/tutorial/). **Building internal tools is like, the exact thing we designed Electric to solve.** I wonder how many hours to get a thread dumper deployed into prod?

To cut to the chase, the answer is: **zero hours. Like 20 minutes.** Here it is:

<div style="margin-bottom: 2em;"></div>

!target-nochrome[docs-site.blog.threaddump1/ThreadDump1]()

<div style="margin-bottom: 2em;"></div>

How many lines of code do you think to do this? Let's count:

!ns-src[docs-site.blog.threaddump1]()

31 lines. Including the imports. Including the CSS. 31 lines of code to get a JVM thread dump util into prod. And that's because I wanted to do better than a pre with a 10,000 line string, I wanted to be able to filter it.

Okay, maybe you think I'm cheating. What's that `EasyTable` thing?

Well first of all, do you code your virtual scroll components by hand? No, you use some heavy React.js library from NPM that has 2000 LOC, 50 lines of JSON configuration, six wrapper divs, a bunch of css constraints to debug, and two REST APIs. Or, you use ✨ClojureScript✨, worse is better here so every enterprise table uses a *pagination bar* like it's 2009.

But still it's a fair ask to see the dependency, so let's take a look:

!ns-src[dustingetz.easy-table]()

43 LOC, plus a bit more for an example of how to use it. Including requires, and my incredibly lazy-coded `Load-css` helper (L24) which *loads css through the freaking electric websocket* because that one-liner to talk to the server is *far* easier than spending a day (week?) futzing around with symlinks and classpaths and jar files just to figure out how to get component-local css on the page for this throwaway blog post.

Remember, I did this in 20 minutes! Don't believe me? How long would it take you, to:
* ask Claude how to get a threaddump - 2 mins
* try it at Clojure REPL and get it working - 5 mins
* wait for clojure repl to start and for clojurescript to compile - 5 mins (because I touched my phone)
* click like 9 buttons in my IDE to make a new namespace with the right includes - 3 minutes
* copy/paste the EasyTable demo from the namespace, change the title and :class - 2 mins
* split the huge string into lines, and filter the lines - 1 mins
* write the two lines of CSS - 2 mins
* = 20 mins

I was about to write "and it's not prod code so we can take shortcuts", but, what shortcuts? What actual line of code do you think is not robust? Probably the riskiest bit is the bool flags in `(.dumpAllThreads thread-bean true true)` that Claude wrote, because I have no idea what those do. I most definitely do not care. Works on my machine👍

How long would it have taken to get a working socket to prod and futz around with shells and scripts and Java swing GUIs? Longer than 20 minutes.

## Conclusion

Most businesses cannot afford to build the tools they need to effectively operate the business.

Electric lets you solve your own problems, in an hour, because you don't have to futz around with all the *crap*. Frontend. Backend. JSON serializaton. HTTP. GraphQL. Node modules. React hooks, or react server components, or whatever. Client side database. Backend data model leaking into frontend architecture. It is all *crap*.

What would it be like, if you could solve your actual problems without drowning in *crap*? What if your business could build the tools it needs, on the actual afternoon that you need it? Without sprint planning, and jira tickets, and PMs, and resource planning, and excel budgets? You could just ... build the damn thing, exactly to the shape of the problem at hand, and then it was just ... *done*?

This has been my lived experience dogfooding Electric v3, and with Electric, you too can have this capability. Check out [the Electric tutorial here](/tutorial/), and [sign up for beta access here](https://www.hyperfiddle.net/early-access.html), we are expanding the beta rapidly.

## REBL for the Web

This ThreadDumper MVP is pretty scrappy. I wonder how long it would take to get a proper `clojure.datafy` data browser view so we can get better thread observability in a more structured way? 

Challenge accepted. **Stay tuned for [part 2!](/blog/y20250109_datafy/)**