# Release readiness

## Verdict

**Not releasable to real students yet. Product-foundation candidate in progress.**

The repository now contains substantially more than the original foundation,
but two external requirements still block a real pilot: real institutional
providers and reproducible execution evidence for the current candidate.

## Main

`main` contains the verified technical foundation.

## Active candidate

`feat/product-foundation-v2` currently contains:

- platform authentication routes and JWT issuance;
- encrypted Android access-token storage;
- local expiry/subject inspection;
- observable session invalidation;
- student-scoped academic Room cache;
- one academic repository for remote + offline reads;
- Hoy;
- Agenda day/week modes;
- profile and logout;
- first reusable academic UX components.

## Current external blocker

GitHub Actions workflow runs are being created for the branch, but the latest
runs remain `pending` with no jobs materialized. That is not a passing or
failing build result and must not be represented as either.

Until Actions execute, the candidate requires an independent clean local build
before merge/release.

## Blocking items before pilot

| Area | Status |
|---|---|
| Real IdentityProvider | BLOCKED externally / not configured |
| Real AcademicProvider | BLOCKED externally / not configured |
| Candidate clean build | REQUIRED |
| Candidate Android unit tests | REQUIRED |
| Candidate API tests | REQUIRED |
| Candidate lint | REQUIRED |
| Compose UI tests | Not yet implemented |
| Accessibility audit | Not yet performed on candidate UX |
| Rate limiting | Still required before public auth exposure |
| Signing/versioning | Internal release work remains |
| Privacy/legal review | Required before real student pilot |

## Candidate acceptance scenarios

The candidate should not merge as pilot-ready until all of these are
demonstrated:

1. valid login survives process restart while the token is valid;
2. expiry transitions back to login;
3. a 401 clears the local platform session;
4. student A cannot expose cached data to student B;
5. Hoy works from remote data and from the same student's cache;
6. Agenda can be opened first and performs its own refresh/fallback;
7. logout returns to authentication;
8. no institutional password is persisted.

## Release evidence format

For the merge/release record capture:

- exact HEAD SHA;
- clean build command and result;
- test suites / executed / failed / skipped counts;
- lint errors/warnings;
- debug/release artifact result;
- API smoke result;
- Git status/diff cleanliness;
- known external blockers.

See [Master Plan](../product/MASTER_PLAN.md).
