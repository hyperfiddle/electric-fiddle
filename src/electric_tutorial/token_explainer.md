# RetryToken

!ns[electric-tutorial.token-explainer/TokenExplainer]()

* todo explain `e/RetryToken`
* disabled
* `::ok`
* `(t)`
* `e/Offload` - move the function to a thread pool, so Thread/sleep doesn't block the Electric server which is async. Using an async sleep here is also fine.
* failure handling?
* we call this the "Service" pattern, we will extract a reusable abstraction