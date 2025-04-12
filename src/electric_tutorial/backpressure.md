# Backpressure and Concurrency <span id="title-extra"><span>

<div id="nav"></div>

"UI is a concurrency problem" — Leo Noel

This is just the Two Clocks demo with slight modifications, there is more to learn here.

!ns[electric-tutorial.backpressure/Backpressure]()

What's happening
* The timer `e/system-time-secs` is a float and updates at the browser animation rate, let's say 120hz.
* Clocks are printed to both the DOM (at 120hz) and also the browser console (at 1hz)
* The clocks pause when the browser page is not visible (i.e. you switch tabs), confirm it **[todo electric v3]**
