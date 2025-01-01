# Webview diffs <span id="title-extra"><span>

<div id="nav"></div>

* Let's take a closer look at the diffs (prerequisite: [fizzbuzz tutorial](/tutorial/fizzbuzz))
* **bonus meta themes: functional core imperative shell; separation of concerns**

!ns[electric-tutorial.webview-diffs/WebviewDiffs]()

What's happening
* dynamic column picker
* the column config is reactive/differential
* column diffs are tapped and logged when the config changes

What's really happening in this Column Picker?

!fn-src[electric-tutorial.webview-diffs/ColumnPicker]()

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

* Is the programmer explicitly coordinating statement order? NO, Electric DAG is doing that, Electric is declarative with respect to statement order, Electric will auto-maximize concurrency at every point, racing everything based on the DAG so you don't have to think about statements.
* Is the DOM node final location determined by statement order of DOM effects? NO, electric-dom is responsible for *positioning* elements based on natural AST order (top to bottom, left to right). The checkbox element mount effects are racing actually, and concurrently stream into their proper final location in the DOM!
* Can the system be left in a broken state if an exception occurs? NO, missionary's RAII semantics guarantee that the resource destructor chain is called
* **Conclusion: No, Electric is not imperative.**

Is the computation pure functional? 
* Classically no, effects are interleaved throughout the entire program, which is a DAG and that DAG contains both pure pipeline stages and effectful stages. 
* But the effects are managed by the effect system. The equivalent Haskell program would be pure. Electric is a syntax transform over the exact same computation, which in Haskell would be considered pure. 
* So is it pure, or not? **Does it really matter?**

Meta theme: **"fake separation of concerns"**
* React.js says: HTML and JS should be interleaved actually, the business concern is "view"
* Tailwind says: HTML and CSS should be interleaved actually, the business concern is "layout"
* Electric says: Frontend and Backend should be interweaved actually, the business concern is "UI"
* Electric-dom says: DOM effects and view logic should be interweaved actually, the business concern is "UX"


**"Functional core, imperative shell" is wrong**. The proof is in the pudding: our [TodoMVC implementation](tutorial/todomvc) is 200 LOC *including the backend*. How many LOC is it in your favorite framework? If you still dispute these claims, I challenge you to replicate this ColumnPicker demo, or TodoMVC or any other demo, using any technology of your choice, and let's count LOC and see which is easier to understand and maintain. I built the ColumnPicker in 20 minutes, on a whim, because it felt like a cool idea to try. How long will it take you?