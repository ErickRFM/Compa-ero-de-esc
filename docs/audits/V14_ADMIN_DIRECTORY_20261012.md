# V14 Admin directory search — incremental implementation

Base main SHA: `2f8e3ebdfbdaf821e156ea1b997481321fb5c861`.

- Keeps the existing protected account lifecycle confirmation, MFA proof and server-side authorization unchanged.
- Adds client-side search by display name/email and status filters for active, pending verification/approval, suspended and revoked accounts.
- Renders a bounded 20-user page, with visible count and previous/next controls. Results are sourced only from the authorized admin response.
- Search/filter/page state is reset per account `ownerKey`.
- Adds Kotlin regression tests for filtering, status distinctions and bounds.

Limitations: pagination is client-side only; it does **not** decrease server response size. A new cursor-based API will be required for very large deployments. No change to institutional permissions or the backend is implied.

Validation gate: current PR HEAD must pass Android compile/test/lint, API and emulator instrumentation before merge.
