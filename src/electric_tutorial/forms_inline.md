# Forms with inline submit <span id="title-extra"><span>

<div id="nav"></div>

* Sometimes, you want to submit each field individually (NOT as a form)
* e.g. iOS preferences, or settings page in a b2b saas.
* Just wrap each `Input!` field it's own `Form!`, due to the concurrent `e/amb` structure, the inline forms will interact with the server concurrently without any further effort:

!ns[electric-tutorial.forms-inline/Forms-inline]()

* For this demo, we don't let you fully suppress the buttons (see: `(or (Checkbox show-buttons* :label "show-buttons") ::cqrs/smart)`), but you can just set `:show-buttons` to `false` in your app to make them go away entirely and rely only on the keyboard - which is often what you want! (For example, TodoMVC, Slack)

### Keyboard support

* Try it above! select the numeric field, `up`/`down`, `enter` to submit, `esc` to discard.
* Go to the top of the page - toggle the checkbox - esc to discard - space to toggle again - enter in any field to submit form - button turns yellow and disabled.

<!-- TODO this doesn't match deployed tutorials behavior - revisit -->
<!-- * Note: yes, enter in *any* field will submit *all* fields in the form. -->
<!-- * But - you're actually used to that! We use tab to navigate a form, and we use enter to submit it at the end. -->
<!-- * These are in fact the native browser semantics being exposed, i.e. we didn't implement this behavior! We just setup the DOM properly. I believe the only think we implemented is to submit when buttons are hidden, i.e. there is no `&lt;button type="submit"&gt;` in the form. -->
<!-- * If you want to prevent premature submit, add validation. -->
