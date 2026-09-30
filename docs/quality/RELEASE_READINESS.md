# Release readiness

## Verdict

**Not releasable. Foundation milestone only.**

The technical foundation is sound and verified. There is no product to
release: two placeholder screens, no authentication, no data, and no
institutional integration.

## Verified state

| Check | Result |
|---|---|
| `./gradlew build` | Pass |
| `./gradlew test` | Pass |
| `./gradlew lint` | Pass, 0 errors, 3 warnings |
| Debug APK | Builds, 20.0 MB |
| Minified release APK | Builds, 1.5 MB |
| Test count | 73 distinct tests, 13 suites, 84 executions, 0 failures, 0 skipped |
| `GET /health` | 200, status `up` |
| `GET /ready` | 503, status `down` — **correct with no database** |
| `GET /version` | 200 |
| Mock lockout | Staging and production refuse to boot |

## Why 503 from `/ready` is a pass

With no `MONGODB_URI` configured the service cannot serve data, so `/ready`
answers 503. Reporting readiness while unable to serve traffic is the failure
this endpoint exists to prevent: a load balancer would keep sending students
to an instance that cannot answer.

`/health` still answers 200 because the process is alive, and its body
reports `mongodb: degraded` so the cause is visible. Liveness and readiness
answer different questions and deliberately give different answers.

This is verified in CI by the API smoke step, which asserts **200 for
`/health` and 503 for `/ready`**.

## Blocking items before any real user

| Area | Status |
|---|---|
| Authentication | Not built |
| Token storage on device | Not built, needs `core:security` |
| Any institutional integration | Mock only |
| Any persisted data | None. The database is wired and empty |
| Rate limiting | Not built |
| Location | Not built, and blocked on the privacy model |
| Crash reporting | None. A pilot needs at least a way to receive a report |
| Accessibility audit | Not performed against a real screen |
| Localisation | Spanish strings only, no localisation layer |
| Legal review | Not started |

## Non-blocking observations

- 3 lint warnings, all in Android resources. Worth clearing before Phase 1.
- Duplicate module build configuration across the seven Android modules.
  Tolerable at seven; revisit past ten, when convention plugins start to pay
  for themselves. Recorded in
  [ADR-001](../architecture/ADR-001-MODULE-BOUNDARIES.md).
- The release APK is unsigned and unversioned for distribution. Signing is
  deliberately deferred.

## Pilot readiness checklist

To be completed before a real student sees this app:

1. Authentication with a real session lifecycle.
2. `core:security` storing tokens in the Android Keystore.
3. `core:database` caching a real academic snapshot, encrypted.
4. A real `AcademicProvider` for one school, with malformed-input tests.
5. Rate limiting on authentication.
6. Audit logging for attendance access.
7. A privacy policy and terms a parent could actually read.
8. Accessibility review against real screens, not previews.
9. Crash reporting, at minimum an email path.
10. A Play Store internal-track release.

## Definition of done for the foundation

Met:

- [x] Reproducible build from a clean clone, wrapper only
- [x] API starts with no external infrastructure
- [x] Misconfiguration fails loudly and names the variable
- [x] Mock providers locked out of staging and production
- [x] Android app builds, debug and minified
- [x] 73 distinct tests, all genuinely executed
- [x] Lint clean of errors
- [x] Architecture, security, privacy and quality documented
- [x] CI validates the same tasks locally

## Related

- [Test plan](TEST_PLAN.md)
- [Bug register](BUG_REGISTER.md)
- [Roadmap](../product/ROADMAP.md)
