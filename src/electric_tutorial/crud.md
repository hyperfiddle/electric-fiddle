# Crud

Forms + collection + optimistic updates

!fiddle-ns[](electric-tutorial.crud/Crud)

Big idea:

someone, somewhere, needs to submit atomic edit transactions to the server.
"submit" implies a submit/discard interaction (e.g. enter/esc, or a button) to
submit or discard the edit. Submission can be field level (enter/esc), or form
level, or higher. Submission implies a token, typically from dom/OnAll,
attached to the submit control element. field level means - dom/OnAll at
field, submit/discard is implicit like a spreadsheet. form level means -
dom/On inside fields, collect values, explicit submit/discard buttons.