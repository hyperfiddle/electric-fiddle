# Simple chat app with optimistic updates <span id="title-extra"><span>

<div id="nav"></div>

* A multiplayer chat app with auth and presence, all in one file.
* Try two tabs, presence means you'll see who else is connected!

!ns[electric-tutorial.chat-monitor/ChatMonitor]()

What's happening

* Auth – log in (empty password)
* It's multiplayer, each connected session sees the same messages
* Presence – logged in users can see who else is in the room (try two tabs)
* Messages submit on enter keypress (and clear the input)
* All connected clients see new messages immediately
* The background flashes yellow when something is loading
