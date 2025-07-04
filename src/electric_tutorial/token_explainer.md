# `e/Token` — model latency, success, failure, retry <span id="title-extra"><span>

<div id="nav"></div>

* This demo toggles a boolean on the server, to introduce basic server RPC (remote procedure call) idioms.
* Big idea: `e/Token` turns *callbacks* into *values*.
* This primitive is the basis for transactional event processing in Electric v3.

!ns[electric-tutorial.token-explainer/TokenExplainer]()

What's happening
* There's a button on the frontend, with a callback, that toggles a boolean, stored in a server-side atom.
* Busy state – The button is yellow and disabled until the server effect completes.
* Failure state with retry affordance, when the server effect is rejected.
