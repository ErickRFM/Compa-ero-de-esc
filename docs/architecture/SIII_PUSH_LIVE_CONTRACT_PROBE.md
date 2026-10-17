# Local Siii push contract probe

`SiiiPushLiveContractTest` is an opt-in external HTTP contract test. It starts the actual Ktor CIO push route on an ephemeral `127.0.0.1` port, provisions a synthetic HMAC credential in a unique disposable Mongo database, and invokes the Siii TypeScript sender with a strict section snapshot. Two immutable deliveries must produce actual HTTP 202 then 200, one immutable receipt, and one institutional projection through the existing leased worker. The source Siii institution UUID differs from the credential's target tenant UUID; only the latter owns persistence.

The test does not start the production application graph, provision native identities, expose a mobile reader route, deliver an operational outbox, or use a deployed service. It does not establish Android integration or production readiness. Source changes still require signed replacement snapshots or tombstones for revocation.

Configure these variables in the local test process:

- `V12_TEST_MONGODB_URI=mongodb://127.0.0.1:27017` (the only accepted database origin).
- `COMPANION_PUSH_PROBE_PROJECT_ROOT`: an absolute Siii worktree containing `tests/support/companion-push-live-probe.ts` and installed `tsx`.
- `COMPANION_PUSH_PROBE_NODE`: optional Node executable; defaults to `node` on PATH.

Run with Java 17:

```powershell
.\gradlew.bat :services:api:test --tests 'org.companerodeescuela.api.integrations.academic.SiiiPushLiveContractTest' --rerun-tasks --no-build-cache --no-configuration-cache --no-parallel --max-workers=1 '-Pkotlin.incremental=false' '-Pkotlin.compiler.execution.strategy=in-process' --console=plain
```

`--rerun-tasks` ensures a preceding opt-in skip is not reused. Without `COMPANION_PUSH_PROBE_PROJECT_ROOT`, the test is explicitly skipped.

The child receives only operating-system runtime paths and five ephemeral variables: `COMPANION_PUSH_PROBE_ORIGIN`, `COMPANION_PUSH_PROBE_SOURCE_INSTITUTION_ID`, `COMPANION_PUSH_PROBE_TARGET_TENANT_ID`, `COMPANION_PUSH_PROBE_CREDENTIAL_ID`, and `COMPANION_PUSH_PROBE_SECRET_BASE64`. The raw 32-byte secret uses standard Base64 and never appears in process arguments. No parent database/API credentials are forwarded.

The sender must emit exactly one JSON object containing `eventId`, `sectionId`, `bodySha256` (SHA-256 of immutable raw envelope bytes), `statuses: ['accepted', 'accepted']`, and `negativeResults: {stale: 401, replay: 401, wrongKey: 401}`. Actual HTTP codes are observed by the Ktor test hook, so the production sender result type needs no test metadata. The six requests must return `[202, 200, 401, 200, 401, 401]`: initial delivery, immutable retry, stale signature, fresh nonce, replayed nonce, and invalid signature. They must leave one receipt. Child output is bounded and suppressed on failure, and its temporary file is deleted. The local Ktor listener and the unique `v12_test_*` database are cleaned up in `finally` blocks.

Validated locally on 2026-10-17 at 23:28:52 UTC against the real Siii TypeScript sender and disposable Mongo 8.2.6: one test passed, zero failures/errors/skips, 3.372 seconds. Ktor observed all six expected HTTP codes, the raw-body SHA-256 matched the single Mongo receipt, the leased worker produced one strictly validated institutional snapshot, the source institution UUID did not own the target, and no native account was created. Cold Java 17 compilation plus scoped formatting/check completed successfully in 2m28s. This evidence covers the local signed HTTP/receiver/projection boundary; it does not cover operational outbox writers, production onboarding, or Android.
