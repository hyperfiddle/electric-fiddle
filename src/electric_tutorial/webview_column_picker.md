# Rejecting "Functional Core Imperative Shell" — Webview column picker <span id="title-extra"><span>

<div id="nav"></div>

Here we add to the previous tutorial a dynamic column picker component in 7 LOC, and then we discuss the meta implications of this with respect to how software is architected today.

!ns[electric-tutorial.webview-column-picker/WebviewColumnPicker]()

What's happening
* dynamic column picker
* table is responsive to column selection
* IO reflows automatically

Closer look: what's really happening in the `Column Picker` impl?

!fn-src[electric-tutorial.webview-column-picker/ColumnPicker]()

reflect on `;reactive ui w/ inline dom!`: this 7 LOC column picker expresses so much in so little code
* DOM writes - maintain a collection of checkboxes
* DOM reads - collecting values derived from events
* data transformation and processing
* everything is reactive
* everything composes as functions
* the user's call convention is just a function call (+ implicit `dom/node` in dynamic scope)
* *local reasoning*: no tangled spaghetti code, when refactoring everything is all in the same place. same file, same function, *same expression*!

Is it "functional core, imperative shell"?
* No, it is not! The effects are on the *inside*!

Does that mean it's imperative?

* Is the programmer explicitly coordinating **statement order?** **NO**, the Electric DAG is doing that, Electric is declarative with respect to statement order, Electric will auto-maximize concurrency at every point, racing everything based on the DAG so you don't have to think about statements.
* Is the DOM node final location determined by **statement order** of DOM effects? **NO**, electric-dom is responsible for the *positioning and ordering* of elements based on natural AST order (top to bottom, left to right). In other words, the checkbox element mount effects are **concurrent in time and yet ordered in space**, ultimately streaming into their proper final location in the DOM regardless of the time ordering in which the effects ran.
* Can the system be **left in a broken state** if an exception occurs like other imperative systems? **NO**, missionary's RAII semantics guarantee that the resource destructor chain is called
* **Conclusion: NO, Electric is not imperative.**

Is the computation pure functional?
* Classically no, effects are interleaved throughout the entire program, which is a DAG and that DAG contains both pure pipeline stages and effectful stages.
* But the effects are managed by the effect system. The equivalent Haskell program would be pure. Electric is a syntax transform over the exact same computation, which in Haskell would be considered pure.
* So is it pure, or not? **Does it really matter?**

Meta theme: **"fake separation of concerns"**
* React.js says: HTML and JS should be interleaved actually, the business concern is "view"
* Tailwind says: HTML and CSS should be interleaved actually, the business concern is "layout"
* Electric says: Frontend and Backend should be interweaved actually, the business concern is "UI"
* Electric-dom says: DOM effects and view logic should be interweaved actually, for a similar authoring experience to that of React but more composable/powerful/expressive, and probably faster in the end too (React has had a decade of optimization work, our implementation is still naive!)


**"Functional core, imperative shell" is wrong**. The proof is in the pudding: our [TodoMVC implementation](/tutorial/todomvc) is 200 LOC *including the backend*. I challenge anyone who disputes these claims to replicate this ColumnPicker demo, or TodoMVC or any other demo, using any technology of their choice. Let's count LOC and see which is easier to understand and maintain. I built the ColumnPicker in 20 minutes, on a whim, because it felt like a cool idea to try. How many LOC is it in your favorite framework, how long will it take to build?