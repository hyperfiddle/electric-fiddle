# Forms6-inline-submit with proper "spreadsheet" UX

What's happening

* including create-new with retry (the hard case)
* Auto-commit fields -
  - todo affordance for create/discard inline (no buttons)
    - possible debug mode with inline buttons/stage?
* optimistic list updates
* pending, ok, fail, retry, including optimistic create new with retry (the hard case)
* inflight cancellation
* layered stages ?

# Scratch

* inline commit/discard (it can be integrated w/ the controls)
* implies that the control disables while committing, you must cancel to edit (it can auto-cancel)
* retry and resubmit
* means the impl must use dom/on, not dom/on-all - user is done here unless they cancel!


Semantics
* CHeckbox is the edge case - it auto submits with each transition, because there are no possible invalid transition states
* Numeric - hold down arrow to scroll through nuimbers (or IOS slot machine UI lets you spin throughj selections). THis is transitory dirty state. You do NOT want to smash the database with hundreds of txns as you scroll through these states! Therefore, the buttons on the numeric do NOT have auto-submit semantics. You must press enter to submit (or discard) if this state is transacting into a database! (If this is a pure functional data flow circuit, this does not apply!)
* Free text - like number, has transitory states, so must submit/discard to database. If it was pure functional (no database transaction), this does not apply.q