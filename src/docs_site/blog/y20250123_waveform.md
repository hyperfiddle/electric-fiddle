# Waveform demo — Electric Clojure

*by Dustin Getz, 2025 Jan 22*

Server-streamed waveform demo in [Electric Clojure](https://github.com/hyperfiddle/electric). Not bad for <50 LOC!

!target[docs-site.blog.waveform0/Waveform0]()

What's happening
* two waves, they play forward
* the cosine function is on the server, of course
* the center record (under the red cursor) is printed below the wave in the debug string 

Code wise, there's not much to see. It's just another rendering of a virtual scroll table, using the exact same `IndexRing` trick we use in the [Explorer example](/tutorial/explorer/), except render to svg/rect instead of table/tr. Everything else is just some css and a clock. We draw two of them for fun.

!ns-src[docs-site.blog.waveform0]()



A learning that surprised us, is that "cool visual tools apps" like this are actually simpler than CRUD apps. We designed Electric specifically for the business application use case, but we found that early adopters quickly started building interesting visual tool prototypes, and even today, only a few companies are building traditional CRUD apps with transactional database forms and such. We were all caught off guard – how can this be?

What we've realized is that the state space of a typical CRUD app is actually *larger* than a typical visual tool. Consider, your typical business application — with all its picklists, its dynamic dependencies, all its intertwined queries and server actions, all its failure states and all its constantly shifting network topologies as the user interacts and the database evolves forward. Plus all those DOM effects, maybe you're hitting microservices, you're weaving together async data streams from different data sources updating at different races and different latency/failure scenarios. It's just a huge state space that these apps have, which is why they take so many LOC and team-years to build (millions of dollars!), and even with these huge engineering investments they break all the time because anything/everything can fail. The failure state space grows polynomially with each feature, each component - and polynomial complexity grows faster than the log-curve productivity you get with a hierarchically managed engineering org.

Compare such an app to e.g. a Studio UI for editing audio/video — that's actually not very dynamic at all! There's a time axis, the buffer is a pure function, there's playback controls but waves are pure functions of time. So we were shocked to realize that the state space of a real-time UI like this one is actually quantifiably smaller than the state space of a business application.

Ok, but how can we objectively measure to find out if this is true? Well, an easy way to quantify this is by counting the LOC needed to express these apps in Electric (and with no loss of operational fidelity, only high performance allowed or the measurement isn't fair!). Let's break it down in this demo:

**48 total LOC** =
* 9 empty lines
* 7 lines of imports
* **2 lines of control flow** - one `e/for` and one `if`
* 2 lines for the two waves - cos and sin
* 28 lines of HTML & SVG presentation to draw the wave with a bit of math

That's 58% presentation, 33% whitespace and imports, leaving only 9% for the actual essential structure of this applications - the two waves, the control flow for the virtual scroll, and the client server logic.

Speaking of client/server logic, where is it? This is a server-streamed web app, so where is the IO complexity, how many LOC of client/server crap? Go try to find it! The answer is 0%! **THERE ISN'T ANY!!**

--

P.S. This demo is a great explainer for `IndexRing`, which is manipulating the diffs to cause the tape striping pattern in the DOM (open the dev tools and see). It is recycling elements in a ring, which we ended up at because it is the most optimal dom layout strategy that results in the fewest possible touches. So, instead of issuing `:grow` and `:shrink` diffs at the two ends (i.e. mount and unmount in classic React terms - to destroy and rebuild the elements), IndexRing is issuing `:update` diffs in a specific pattern. In some cases, if the userland row renderer is careful, that means the total set of dom touches may be precisely text nodes only (i.e. inside the row but not the row itself), which means the layout engine can treat the rows as fixed boxes and perform the reordering and repainting exclusively on the GPU, which just feels incredibly lightweight and fast. That is not what is happening here, but we'll clean that POC up and publish it at some point.