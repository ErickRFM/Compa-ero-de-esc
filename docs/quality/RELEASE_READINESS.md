# Release readiness

## Verdict

**Internal product foundation is merged and reproducibly green. Real-student pilot remains blocked by external institutional integrations and physical acceptance evidence.**

## Main baseline

`main@61a01cb29592a988f97700772674637eedeb843d`

Merged product foundation includes:

- platform authentication and short-lived JWT issuance;
- encrypted Android access-token storage;
- local expiry/subject inspection;
- observable session invalidation;
- student-scoped Room academic cache;
- canonical remote/offline academic repository;
- Hoy;
- Agenda day/week modes;
- profile/logout;
- reusable academic UI components.

## Verified CI evidence

The merged candidate passed both required workflows before merge:

### API

- compile backend/shared: PASS;
- backend/shared tests: PASS;
- test-execution verification: PASS;
- runnable service assembly: PASS;
- smoke test: PASS;
- secret scan: PASS;
- secret-scan self-test: PASS.

### Android

- compile Android sources: PASS;
- Android unit tests: PASS;
- test-execution verification: PASS;
- lint: PASS;
- lint errors: 0;
- debug APK build: PASS;
- minified release APK build: PASS;
- release artifact validation: PASS;
- secret scan: PASS.

The Android suite executed 36 tests across 9 report suites with 0 failures and 0 errors in the product-foundation closure run.

## Security / isolation evidence

Automated tests cover:

- expired access token returning to authentication;
- 401 session invalidation;
- cache ownership isolation between students;
- remote academic response ownership matching the JWT subject;
- offline fallback only for the authenticated student;
- no institutional password persistence in the Android session store.

## Internal hardening in progress

Login abuse controls are being landed directly on top of main:

- bounded attempt window;
- normalized username + source-address bucket;
- Retry-After on 429;
- successful-login reset;
- deterministic limiter tests.

This protects a first single-instance pilot API. A multi-replica deployment requires a shared limiter or gateway enforcement.

## External blockers before real-student pilot

| Area | Status |
|---|---|
| Real IdentityProvider | BLOCKED externally / not configured |
| Real AcademicProvider | BLOCKED externally / not configured |
| Real school test accounts | BLOCKED externally |
| Compose device/UI acceptance | REQUIRED |
| Accessibility physical audit | REQUIRED |
| Internal signing/version policy | REQUIRED before distribution |
| Privacy/legal review | REQUIRED before real-student pilot |
| Rate limiting | CODED / merging after product-foundation squash |
| Attendance | NEXT PRODUCT PHASE |
| Dynamic QR | LATER, after attendance correctness |

## M1 acceptance status

| Scenario | Automated / build evidence | Physical / external evidence |
|---|---|---|
| valid login survives process restart while token remains valid | PASS | physical confirmation pending |
| expiry returns to login | PASS | physical confirmation pending |
| 401 clears platform session | PASS | physical confirmation pending |
| student A cannot expose student B cache | PASS | — |
| Hoy works remote + same-student cache | PASS | real provider pending |
| Agenda refresh/fallback independently | PASS | real provider pending |
| logout returns to authentication | PASS | physical confirmation pending |
| institutional password is not persisted | PASS | — |
| real timetable on real school account | — | BLOCKED by provider integration |

## Release classification

Current classification:

`INTERNAL_FOUNDATION_READY / PILOT_BLOCKED_EXTERNALLY`

Do not label the app production-ready for real students until the real institutional providers and physical pilot acceptance are complete.

See [Master Plan](../product/MASTER_PLAN.md) and [Execution Plan](../product/EXECUTION_PLAN.md).
