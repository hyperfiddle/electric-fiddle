# Waveform demo — Electric Clojure

*by Dustin Getz, 2025 Jan 22*

Server-streamed waveform demo in [Electric Clojure](https://github.com/hyperfiddle/electric). Not bad for 43 LOC!

!target[docs-site.blog.waveform0/Waveform0]()

What's happening
* two waves, they play forward
* the cosine function is on the server, of course
* the red cursor is a record selector, hover a record to target it in the debug view

Code wise, there's not much to see. It's just another rendering of a virtual scroll table, using the exact same `IndexRing` trick we use in the [Explorer example](/tutorial/explorer/), except render to svg/rect instead of table/tr. Everything else is just some css and a clock. We draw two of them for fun.

!ns-src[docs-site.blog.waveform0]()

A learning that surprised us, is that we built electric originally for the internal tools use case, but what we found is that the state space of a typical CRUD app is actually *larger* than a typical visual tool, which we can quantify and objectively measure through the LOC needed to express them in Electric.

Consider, your typical business application — with all its picklists, it is dynamic dependencies and all its intertwined queries and server actions, and all its failure states and all its constantly shifting network topologies as the user interacts. Plus all those DOM effects, maybe you're hitting microservices, you're weaving together async data streams from different data sources and different latency/failure characteristics. It's just a huge state space that these apps have, which is why they take so many LOC and team years to build. 

Compare this to editing say an audio stream — that's actually not very dynamic at all! There's a time axis, it's a pure function, there's hardly any control flow at all. So we were shocked to realize that the state space of a real-time UI like this one is actually quantifiably smaller than the state space of a business application. As demonstrated by this demo being 43 LOC, almost all of which is the styles and math in connection with the drawing of the wave, not the client/server logic which is dead simple.

--

P.S. This demo is a great explainer for `IndexRing`, which is manipulating the diffs to cause the tape striping pattern in the DOM (open the dev tools and see). It is recycling elements in a ring, which we ended up at because it is the most optimal dom layout strategy that results in the fewest possible touches. So, instead of issuing `:grow` and `:shrink` diffs at the two ends (i.e. mount and unmount in classic React terms - to destroy and rebuild the elements), IndexRing is issuing `:update` diffs in a specific pattern. In some cases, if the userland row renderer is careful, that means the total set of dom touches may be precisely text nodes only (i.e. inside the row but not the row itself), which means the layout engine can treat the rows as fixed boxes and perform the reordering and repainting exclusively on the GPU, which just feels incredibly lightweight and fast. That is not what is happening here, but we'll clean that POC up and publish it at some point.