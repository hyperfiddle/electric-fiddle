# How to ask for support in a way that minimizes the amount of work we have to do to help

* Format like: "expected behavior: X \n actual behavior: X"
* the post is self-contained, i.e. no "context" is needed to understand.
* if your issue is a question, it should contain a clear & answerable question, marked with a question mark "?"
* "it doesn't work" is not a question, it is neither clear nor answerable, because it contains two indirect references: "it" and "work". These references resolve to context that only exists in your mind, but not ours, so we cannot resolve them!
* one top level post per issue. Do not link to other threads, do not expect us to recall other threads
* assume that I/we are reading and responding on mobile from the couch/toilet, because to be honest we probably are. That means: be concise, format code snippets correctly, any code snippets longer than 3 lines should be uploaded as a slack attachment
* If you claim an electric bug, that is a very strong claim, you must demonstrate it with a minimized program (like 3 LOC) with zero third party dependencies. If you cannot demonstrate it (i.e. *prove*), then you are certainly wrong, please instead ask a question
* If a third party dependency is part of the interaction, then that is not an "electric bug" it is an "interaction with third party dependency". Please use accurate language and claim the correct thing. This may seem pedantic, but if you claim the wrong thing, we basically instantly bucket the issue into "user is confused, not actually a real bug" and we are unlikely to be interested in doing the work to parse the confusion and extract a clear answerable question. See #2
* Post support issues in the channel, not DMs.

Big Idea: if you do all of the above well, you will have provided a clear/concise issue description and we can likely understand your issue in 15 seconds, and give you an answer in a couple minutes. If you don't, then the effort to parse and understand your issue will spill into the 20 minute to 2 hour range, which is too much to fit into the cracks of our day (between meetings, waiting for deploys, etc), and therefore your issue is now backlogged, and our backlog is infinite.