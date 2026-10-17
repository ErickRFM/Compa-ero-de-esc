# Siii institutional academic reads

`SiiiAcademicProvider` implements the existing `AcademicProvider` seam against
Siii's `/api/integrations/companion/v1` contract. Android continues to call Ktor;
only the server holds the institution's bearer credential. External DTOs stay
inside the provider boundary and existing `AcademicMappers` remain authoritative
for the application's contracts.

The adapter reads the student, academic load, courses, teachers, and recurring
schedule endpoints. It requires an HTTPS origin, a server-only bearer token, and
a bounded timeout. HTTP loopback is available only through an explicit option
for synthetic local tests. Redirects are disabled, response sizes are bounded,
and upstream bodies, URLs, and credentials are excluded from public errors.
The adapter adds no application-level retries or fabricated fallback records.

The client uses HTTP/1.1 explicitly. The real local Next server closes JDK's
default cleartext HTTP/2 upgrade request with EOF; the same authenticated read
succeeds with HTTP/1.1. This protocol choice preserves TLS and all
credential, redirect, timeout, and response-limit controls.

Use `ProviderRegistry.configured(...)` in the server composition root and supply
each existing provider explicitly. Construct the academic provider using the
Siii origin and the same random base64url credential whose SHA-256 hash is stored
in Siii's `SIII_COMPANERO_TOKEN_SHA256`. Siii selects its institution using
`SIII_COMPANERO_INSTITUTION_ID`. This credential currently serves one institution;
do not share this provider across tenants. The existing environment check still
rejects every mock provider in staging and production. This change does not
select an adapter automatically or supply missing real identity/LMS/library/
events/sync implementations.

HTTP 404 returns null only for `getStudent`; missing academic load or schedule
raises `NOT_FOUND`. HTTP 401/403 maps to `UNAUTHORIZED`; other request/conflict
errors map to `BAD_REQUEST`; HTTP 429/5xx/redirects map to `UNAVAILABLE`.
Invalid successful payloads map to `MALFORMED_RESPONSE`, and expired request
budgets map to `TIMEOUT`. Callers receive the existing `IntegrationException`.

Siii currently resolves schedules by student enrollment number. Teacher schedule
lookup is not supplied by this Siii v1 contract. The `weekOf` date is forwarded,
but Siii validates it while returning recurring patterns; dated cancellations
and exceptions cannot be represented by `ExternalScheduleSlot`.

Siii permits a pattern ending at minute 1440 and emits `24:00`. The existing
`AcademicMappers`/`LocalTime` contract cannot represent that end-of-day value or
an overnight interval faithfully. This adapter rejects it as
`MALFORMED_RESPONSE`; it never truncates the class to 23:59 or assigns a guessed
date. A dated occurrence contract is required before those patterns can be
consumed through Ktor.

## Remaining end-to-end dependencies

This adapter does not implement the signed push projection pipeline. Current
`InstitutionalEventProjectionWorker` wiring retains its unsupported adapter and
unavailable handler behavior. Replacing these with successful no-ops would lose
institutional changes and must not be used as an integration shortcut.

The remaining pipeline needs versioned entity payload schemas shared with Siii,
a Siii transactional outbox and HMAC sender, actual tenant-scoped idempotent
projection handlers that guard `sourceVersion`, durable projection readers,
existing native identity links, and real retry/reconciliation tests. Ktor's
current event envelope does not include grades in its allowed entity types.

Existing native authenticated accounts are served through
`NativeAcademicProjection`, which intentionally does not call `AcademicProvider`.
Institutional sessions use the provider seam. Connecting the new read adapter
alone therefore does not make Siii records appear for native accounts or change
identity ownership. A deployed Ktor↔Siii test and a native account projection
test are still required before claiming complete mobile integration.

The focused adapter tests use an actual loopback HTTP server with synthetic
fixtures. They exercise the five endpoint paths, bearer forwarding, Saturday
schedules, failures, malformed payloads, response limits, origin/identifier
validation, redirects, and request timeouts. They do not connect to deployed
systems or read production data.

`SiiiLiveContractTest` is an additional opt-in probe against a real disposable
Siii Next/PostgreSQL instance. It rejects every non-loopback host. Supply the
synthetic fixture variables `SIII_LIVE_TEST_BASE_URL`, `SIII_LIVE_TEST_TOKEN`,
`SIII_LIVE_TEST_STUDENT_ID` (enrollment number), `SIII_LIVE_TEST_COURSE_ID`
(section UUID), `SIII_LIVE_TEST_TEACHER_ID` (employee number),
`SIII_LIVE_TEST_CLASSROOM_CODE`, and `SIII_LIVE_TEST_WEEKDAY`. It verifies all
five endpoints and their student/course/teacher/schedule references. Without
the base URL it is explicitly skipped. Run it fresh after seeding the lab:

```powershell
.\gradlew.bat :services:api:cleanTest :services:api:test --tests '*SiiiLiveContractTest' --no-build-cache --console=plain
```

`cleanTest` plus `--no-build-cache` prevents a previous skipped probe from being
mistaken for a live contract result. Keep the bearer in the runner's ephemeral
environment; never put it in command arguments, logs, or committed fixtures.

## Local validation (2026-10-17)

- Full backend/shared run and `:services:api:installDist`: successful. API
  554 tests, zero failures, initially 61 Mongo-dependent tests skipped;
  contracts 18 and validation 7 tests passed.
- Final focused `--tests '*Siii*Test'`: 10 passed; the opt-in live probe was
  skipped without its lab environment.
- A real Next preflight reproduced HTTP/2-upgrade `IOException`/`EOFException`
  and HTTP/1.1 success with the same synthetic credential. The HTTP-server
  upgrade regression failed before explicitly selecting HTTP/1.1 and passed
  after the change.
- Real `SiiiLiveContractTest` against the restricted local Next/PostgreSQL lab
  passed after the protocol fix: one test, zero failures/errors/skips,
  `BUILD SUCCESSFUL` in 16 seconds (2026-10-17 22:24:04–22:24:21 UTC).
  All five actual endpoints preserved the synthetic student, enrolled course,
  teacher, Saturday classroom, and 10:00–11:00 time window. No production
  writes, Android validation, or native account projection were involved.
- Disposable MongoDB 8.2.6 at `127.0.0.1:27017`,
  `V12_TEST_MONGODB_URI=mongodb://127.0.0.1:27017`,
  `--tests '*Mongo*Test'`: 71 passed, zero failures or skips. This run covers
  the previously skipped Mongo tests, including institutional receipts,
  projection leases, version monotonicity, and native identity links.
- ktlint 1.5.0 passed on the six changed Kotlin files. This branch's full CI
  results must still be observed before merge.
- The repository's `infrastructure/scripts/secret-scan.sh` passed with all
  seven changed files staged.
