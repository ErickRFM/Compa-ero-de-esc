# MongoDB readiness timeout handling

The MongoDB readiness probe has its own three-second budget. Expiring that
budget means the dependency is unavailable; it must not cancel the HTTP
request. A Mongo outage therefore keeps `GET /health` at HTTP 200 with
`DEGRADED` and makes `GET /ready` HTTP 503 with `DOWN`. Cancellation of an
external caller or application shutdown must still propagate normally.

The probe uses `withTimeoutOrNull` to distinguish its own budget from an
external cancellation. Driver failures remain false results and only their
exception class is logged. Health responses do not include connection strings,
credentials, hosts, or driver stack traces.

The driver client now applies `MONGODB_CONNECT_TIMEOUT_MS` and
`MONGODB_SERVER_SELECTION_TIMEOUT_MS`, already parsed by `ApiSettings`.
Application timeout values take precedence over values in `MONGODB_URI`;
other URI options remain in force. Both application settings default to
5000 ms. The independent ping budget remains 3000 ms.

This change does not repair an unavailable MongoDB deployment or alter any
connection string, credentials, network access, authentication, or deployment
configuration. It makes an outage observable through the existing health
contract rather than an internal server error. SRV/DNS resolution and network
connectivity must still be diagnosed separately if the dependency stays down.

## Regression evidence

Before the fix, real driver tests against a reserved loopback TCP socket that
never speaks MongoDB reproduced `TimeoutCancellationException` for the probe's
own budget. Through the actual application module the same fixture produced
HTTP 500 for both operational endpoints instead of 200/503. The external
caller cancellation test passed before the change and must continue to pass.

The focused tests cover the own budget, caller cancellation, effective driver
settings and URI precedence, server selection duration, real HTTP status
mapping, and an opt-in positive ping against a synthetic loopback MongoDB.
The positive test requires `V12_TEST_MONGODB_URI` in the strict form
`mongodb://127.0.0.1:PORT` or `mongodb://localhost:PORT`; no remote or multi-host
URI is accepted. It issues only `ping`, without creating or modifying data.

## Verification commands

```powershell
$env:V12_TEST_MONGODB_URI='mongodb://127.0.0.1:27017'
.\gradlew.bat :services:api:test --tests '*Health*Test' --no-parallel --max-workers=1 '-Pkotlin.incremental=false' '-Pkotlin.compiler.execution.strategy=in-process' --console=plain
```

The changed Kotlin files are checked with ktlint 1.5.0 using explicit file paths.
Repository-wide formatting is outside this bug fix.

## Local verification (2026-10-17)

- Red probe run: two tests failed with `TimeoutCancellationException`; external
  cancellation propagation already passed.
- Red HTTP run: `/health` returned 500 instead of 200, and `/ready` returned
  500 instead of 503 against the same local driver fixture.
- Red driver options run: effective connect timeout was 9000 ms from the URI,
  instead of the explicit 50 ms application setting.
- Green `--tests '*Health*Test'` with the loopback MongoDB lab enabled:
  **20 passed, zero failures or skips**. This includes actual driver and HTTP
  regressions plus the existing health contracts.
- ktlint 1.5.0 on the three explicitly named changed Kotlin files: successful.
  `git diff --check`: successful.
- Full backend/shared run with the loopback MongoDB lab enabled completed
  successfully: API **553 tests, zero failures, errors, or skips**. Shared
  contracts/validation reused valid Gradle results; models also executed.
