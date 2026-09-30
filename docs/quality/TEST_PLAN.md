# Test plan

## Current position

| | |
|---|---|
| Distinct tests | 92 |
| Distinct suites | 14 |
| Executions reported by Gradle | 118 |
| Report files | 16 |
| Failures | 0 |
| Skipped | 0 |

**On the difference between 92 and 118.** The Android modules have two build
variants, so `testDebugUnitTest` and `testReleaseUnitTest` each execute the
same 26 tests. Gradle therefore reports 66 JVM + 26 debug + 26 release = 118
executions, across 16 report files. The honest count of distinct tests is
**92**. Both numbers are given so neither is mistaken for the other.

Counting distinct tests by suite *and* variant is the easy mistake: it reports
118, overstating coverage by 26 tests that exist once. The suite name is the
unit, not the suite/variant pair.

**The distinction between discovered, executed and skipped is tracked
deliberately.** A suite that compiles but never runs reports zero tests and
does not fail. That happened here, and it is recorded in the
[bug register](BUG_REGISTER.md).

## Verification before declaring done

Counts are taken from the JUnit XML reports, not from the Gradle exit code.

```powershell
$files = Get-ChildItem -Recurse -Filter "TEST-*.xml" | Where-Object { $_.FullName -match "test-results" }
$t = 0; $f = 0
foreach ($x in $files) {
    [xml]$d = Get-Content $x.FullName
    $t += [int]$d.testsuite.tests
    $f += [int]$d.testsuite.failures + [int]$d.testsuite.errors
}
"suites=$($files.Count) tests=$t failures=$f"
```

## What is covered

### Shared contracts — 6 tests

`UserRoleTest`, `UserSummaryTest`, `HealthResponseSerializationTest`

Serialization is part of the contract, so it is tested rather than assumed.
These tests are what makes it safe for Android and the API to share these
types.

### Shared validation — 7 tests

`ValidatorsTest`: blank, length and format rejection, and that
`ValidationResult` names the offending field without echoing the rejected
value.

### Academic mappers — 14 tests

`AcademicMappersTest` is the highest-value suite in the repository, because
the mapper is the boundary that keeps institutional quirks out of the product.

Covered: trimming, email lowercasing, blank-to-null, weekday parsing in
Spanish and English, time parsing, unknown-course degradation, classroom
resolution, per-day ordering, and explicit failure on an unrecognised
weekday, an inverted slot and an unparsable time.

### Settings and environment — 13 tests

`EnvironmentTest`, `SettingsLoaderTest`: required variables, the production JWT
rule, and that a rejected value is never echoed into an error message.

### Health — 13 tests

`HealthServiceTest`: UP / DEGRADED / DOWN aggregation, readiness requiring
the database, and that no response leaks the connection string.

`HealthRoutesTest` boots the **real application module** through
`testApplication`, so routing, serialization, plugins and the 404 error
contract are covered together rather than mocked individually.

### Providers — 9 tests

`MockProviderTest`, `ProviderRegistryTest`: fixtures, not-found behaviour,
wrong-password and unknown-user indistinguishability, and the registry
refusing mocks outside local and development.

### Android: network — 20 tests

`ApiCallTest` drives the **real Ktor client** through `MockEngine`, so the
request pipeline, plugins and error mapping are covered together.

- a parameterized matrix over every status the API can return (400, 401, 403,
  404, 409, 422, 500, 502, 503) asserting the status is preserved, the retry
  policy is right and each one has a presentable message;
- 200 with a valid body deserialises into the contract type;
- 204 with no body is a unit success rather than a serialization failure;
- a 200 whose body is well-formed JSON of the wrong shape is still a contract
  break;
- a unit endpoint never captures the error body at all, and a body-reading
  endpoint caps it at `MAX_ERROR_BODY` for the log; neither reaches a
  user-facing message;
- a timeout and a dropped connection are both retryable network failures;
- the base URL must be absolute and end with a slash.

429 is absent on purpose: no endpoint rate-limits yet, and a row for a
behaviour that does not exist would document a fiction.

### Android: UI state — 5 tests

`ContentStateTest`: the Outcome to ContentState mapping, caller-defined
emptiness, and that every `userMessage` is presentable and free of raw
technical strings.

### Boot path — 4 tests

`ApplicationModuleBootTest` exists because BUG-001 proved that unit-testing a
security rule is not the same as enforcing it. It boots the **real application
module** through `testApplication`:

- production refuses to boot, and the message names the offending providers;
- staging refuses to boot;
- local boots and serves `/health`;
- the guard runs before any route is reachable.

The equivalent is verified against the installed distribution outside the test
suite too, by starting the real process in each environment and confirming the
port never binds.

## Test doubles

| Double | Where | Note |
|---|---|---|
| `FakeMongoConnection` | `services/api/src/test` | Reachable or not, no driver |
| `MockEngine` | `core:network` | Drives the real request pipeline |
| `TestDispatcherProvider` | `core:testing` | Deterministic coroutines |
| `MockAcademicProvider` etc. | `services/api/src/main` | Production mocks, not test doubles |

Mock providers live in `src/main` on purpose: they are what a fresh clone runs
against, so they are real code with real behaviour.

## Conventions

- JUnit 5 everywhere, with `useJUnitPlatform()` in every Android module.
  AGP's JUnit 4 default runs nothing and does not fail.
- Test names state the behaviour, not the method under test.
- `@DisplayName` says what a reader would assert, in plain language.
- Kotlin `assert*` from `kotlin-test`, with explicit type arguments where
  inference fails on a generic sealed type.
- No sleeps. Coroutine tests use `runTest`.

## What is not covered yet

Stated so nobody assumes otherwise.

| Gap | Planned for |
|---|---|
| No instrumented or UI tests | Phase 1 |
| No Compose screenshot or semantics tests | With the first real screen |
| No emulator or device run | Phase 1 |
| No load or performance testing | Before any real traffic |
| No test for `core:designsystem` colours beyond manual contrast | With real screens |
| No coverage of authentication | Does not exist yet |
| No lint-baseline regression test | CI runs lint directly |

## Adding tests

A bug found in this repository gets a test before it gets a fix, in the same
commit. That is why three of the entries in the
[bug register](BUG_REGISTER.md) point at test files: the test is the artefact
that stops the bug returning, and the fix alone does not.

## Related

- [Bug register](BUG_REGISTER.md)
- [Release readiness](RELEASE_READINESS.md)
- [API architecture](../architecture/API_ARCHITECTURE.md)
