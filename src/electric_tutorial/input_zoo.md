# Input zoo

* Five different implementations of inputs. Only the first simplest one is redundant, the other four have orthogonal use cases.

Todo: String, number, checkbox

!fiddle[](electric-tutorial.input-zoo/InputZoo)

Checkboxes, numerics and text inputs have subtly different semantics
* Namely, **text inputs buffer**
* This raises semantics issues like: When to submit a buffered input? How to submit a set of inputs atomically as a form? What submit semantics map to what application capabilities? And how to unify submit semantics across all widget types to enable declarative forms?
* Numeric input, which has up down arrows, and also free text input. If the user interacts via up/down, should the control emit immediately? If the user interacts by free text input, should the control buffer the input? When should buffered input submit - blur, enter, explicit commit button?

<table>
  <tr>
    <td>
      ### Input* — crude uncontrolled state, rarely useful
      <div id="zoo_input_star_demo"></div>

    </td>
    <td>
      <div id="zoo_input_star_demo_src"></div>
      <div id="zoo_input_star_src"></div>
      <div id="zoo_checkbox_star_src"></div>
    </td>
  </tr>
</table>

!fn-src[electric-tutorial.input-zoo/DemoInput*](#zoo_input_star_demo_src)

!fn-src[hyperfiddle.input-zoo0/Input*](#zoo_input_star_src)

!fn-src[hyperfiddle.input-zoo0/Checkbox*](#zoo_checkbox_star_src)

<table>
  <tr>
    <td>
      ### Input — simple dataflow circuit (i.e., "controlled")
      <div id="zoo_input_demo"></div>
    </td>
    <td>
      <div id="zoo_input_demo_src"></div>
      <div id="zoo_input_src"></div>
      <div id="zoo_checkbox_src"></div>
    </td>
  </tr>
</table>

!fn-src[electric-tutorial.input-zoo/DemoInput](#zoo_input_demo_src)

!fn-src[hyperfiddle.input-zoo0/Input](#zoo_input_src)

!fn-src[hyperfiddle.input-zoo0/Checkbox](#zoo_checkbox_src)

<table>
  <tr>
    <td>
      ### Input! — Async remote transaction (RPC) with eager submit
      <div id="zoo_input_bang_demo"></div>
      * 3 states: ok/success, pending, ~~failure~~ (todo).
      * Submits txn requests eagerly (as the user types) but reusing the same token instance, so that a consumer may intercept, buffer and batch the in-flight txns, for example an atomic form with commit/discard control (see **Forms** tutorial).
      * (The problem with Input is - the cycle is in your database! So the entrypoint is forced into the transact! side effect.)
    </td>
    <td>
      <div id="zoo_input_bang_demo_src"></div>
      <div id="zoo_input_bang_src"></div>
      <div id="zoo_checkbox_bang_src"></div>
    </td>
  </tr>
</table>

!fn-src[electric-tutorial.forms/UserForm](#zoo_input_bang_demo_src)

!fn-src[electric-tutorial.input-zoo/DemoInput!](#zoo_input_bang_demo_src)

!fn-src[hyperfiddle.input-zoo0/Input!](#zoo_input_bang_src)

!fn-src[hyperfiddle.input-zoo0/Checkbox!](#zoo_checkbox_bang_src)

<table>
  <tr>
    <td>
      ### InputSubmit!
      <div id="zoo_input_submit_bang_demo"></div>
      <p>

      </p>
    </td>
    <td>
      <div id="zoo_input_submit_bang_demo_src"></div>
      <div id="zoo_input_submit_bang_src"></div>
    </td>
  </tr>
</table>

!fn-src[hyperfiddle.input-zoo0/InputSubmit!](#zoo_input_submit_bang_src)

!fn-src[electric-tutorial.input-zoo/DemoInputSubmit!](#zoo_input_submit_bang_demo_src)

<table>
  <tr>
    <td>
      ### InputSubmitClear!
      <div id="zoo_input_submit_clear_bang_demo"></div>
      <p>
      </p>
    </td>
    <td>
      <div id="zoo_input_submit_clear_bang_demo_src"></div>
      <div id="zoo_input_submit_clear_bang_src"></div>
    </td>
  </tr>
</table>

!fn-src[hyperfiddle.input-zoo0/InputSubmitClear!](#zoo_input_submit_clear_bang_src)

!fn-src[electric-tutorial.input-zoo/DemoInputSubmitClear!](#zoo_input_submit_clear_bang_demo_src)


# More



Desired capabilities
* Observability - all internal states (i.e. pending) afforded in UI
* Composition -
* no debouce commit, commit on blur or other crude hacks
* can express any/all form semantics


Control design space
* controlled vs uncontrolled input. Do we ever want uncontrolled? (Yes!!)
* concurrent write safety and conflict handling
* local (always succeeds synchronously) or remote (async, latency, failure)
* resilience
* what is the difference between dirty and pending?
* buffering - string inputs tend to build up a new value and submit all your keystrokes at the end. but checkboxes, you submit once, imediately. How can we unify these semantics into a single form abstraction?

Application design space
* client only state - ephemeral & controlled
* database edits
* chat apps that submit new messages
* latency, knowing what's pending, failure handling
* forms vs individual inputs
* explicit submit button vs auto-save
* spreadsheet editors (cells submit individually) vs forms (fields submit together, except when fields submit individually)

* create new vs edit
* transaction failure
* pending observability
* composite controls - typeahead
* edit composition - datagrids


Application capabilities
* client-only inputs, such as search and filtering, or debug parameters
* transactional inputs bound to a durable server database entity. The RU in CRUD – they Read and Update, but never Create or Delete.
* transactional inputs that Create server database entities.


Electric primitives at our disposal
* e/amb
* e/Token
* dom/On
* dom/On-all
* e/with-cycle
* incseqs


; These inputs have 4 states: ready, dirty, pending, ok, fail(retry).

Electric callback-free idioms for input processing
* `(->> (Input*) (reset! !v))`




Retry!
* InputSubmitClear! cannot have retry
* InputSubmit! can retry, you need to discard the in flight command, therefore it is disabled until discard
* Input! does not disable, it assumes the stage will handle disabled. You must cancel an in-flight commit to continue editing. Therefore, the form is disabled on commit!


CheckboxSubmit! - is it a thing?
* why would we spam many submits? It's a commit/discard situation, losing the cancel/fail/retry affordance is bad



* do not use
* webview search box, typeahead filter

Input - simple controlled, sync, local

* local only, no latency, always succeeds, signal of latest value. Use for ephemeral state e.g. a search bar – you always want latest value, there are never conflicts.
* e.g. (Input "fizz") has atom-like bheavior
* webview search box, typeahead filter

Input! - eagerly submit one txn and reuse the token until committed

* transactional, models latency/success/failure (i.e. conflict handling) and concurrency. e.g. crud forms bound to stateful entity.
* models dirty state and pending
"buffered" i.e. dom/On, reuses token until commit - for use with a staging area where you want to update a single staged edit, not issue multiple independent submits
The difference between Checkbox and Input semantics is that checkboxes generally submit once and you're done. But a string, you want to build up a new value and submit all your keystrokes at the end. Therefore, Checkbox! is useful without a stage, but Input! likely needs a stage. the TodoMVC latency editor uses Input! without a stage, and it eagerly commits new latency with each interaction with the input.
validation? Prevent invalid submits?
transactional, entity backed (?)
what about rapid entry?

InputSubmit!

* submit txns on enter (no clear) – todomvc todo editor
* transactional + clears the input when submitted, e.g. a chat box.
* dom/onAll, isolated edit sequence to database
* controlled!

PendingMonitor

e/with-cycle

e/amb to merge inflight edits

* using this to collapse inputs
* dom/On returns one edit
* dom/On-all returns multiplexed edits
* those have the same type

neutral siting

*

There may be bugs! Let me know if you can crash it.

what is an "edit"