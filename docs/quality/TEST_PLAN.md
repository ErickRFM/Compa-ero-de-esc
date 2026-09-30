# Test plan

## Current position

| | |
|---|---|
| Distinct tests | 73 |
| Distinct suites | 13 |
| Executions reported by Gradle | 84 |
| Failures | 0 |
| Skipped | 0 |

**On the difference between 73 and 84.** The Android modules have two build
variants, so `testDebugUnitTest` and `testReleaseUnitTest` each execute the
same 11 tests. Gradle therefore reports 62 JVM + 11 debug + 11 release = 84
executions, across 15 report files. The honest count of distinct tests is
**73**. Both numbers are given so neither is mistaken for the other.

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

### Android: network — 6 tests

`ApiCallTest` drives the **real Ktor client** through `MockEngine`: 500
retryable, 401 unauthorized and not retryable, empty success, malformed body
mapped to a serialization failure, and a dropped connection mapped to a
network failure.

### Android: UI state — 5 tests

`ContentStateTest`: the Outcome to ContentState mapping, caller-defined
emptiness, and that every `userMessage` is presentable and free of raw
technical strings.

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
