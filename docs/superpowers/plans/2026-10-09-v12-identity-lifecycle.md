# V12 identity lifecycle implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Persist explicit native account state and atomic audited identity revisions; immediately invalidate old native access and refresh sessions after authority changes.
**Architecture:** Extend PlatformAccountRepository, AuthService, PlatformSessionAuthority and existing JWT/refresh codecs. Keep legacy active=true records working at revision zero; malformed explicit authority fields fail closed. Pending sessions may inspect /auth/me but cannot use protected academic routes.
**Tech Stack:** Kotlin, shared kotlinx.serialization contracts, Ktor JWT, Mongo coroutine driver, Compose consumers unchanged.
**Spec:** User V12 mission at C:/Users/erik5/.codex/attachments/48d0fb68-1d64-4532-89e1-f726b07f8137/Texto pegado.txt. Initial origin/main: e51e516e971f055d89ea5f4c2853417e67431771.

## Global Constraints
- Preserve existing native/institutional authentication, JWT refresh and live authority.
- No public ADMIN/SUPER_ADMIN registration or privileged mutation endpoint; MFA and scoped administrative consumers belong to later V12 deliveries.
- Preserve primary C:/proyectos/esc branch, index and local files; work only in this isolated checkout.
- Separate granted roles from account state; inactive or revoked identities never authenticate.
- No invented production owners, secrets, MFA claims or demo records.

## Review Focus
- Reinstating identical roles must never revive an old native session.
- Pending accounts with accidentally stored academic roles must not obtain academic access.
- Unknown, null or malformed explicit authority fields fail closed; absent legacy fields remain compatible.
- Concurrent mutations and retried request IDs must not duplicate grants or audit entries.
- Production Mongo atomicity must be tested separately from the deterministic driver fixture; disclose any unavailable real database.

### Task 1: Account state and authority snapshots
**Files:** shared/contracts/.../AccountStatus.kt, UserSummary.kt; services/api/.../auth/PlatformAccountRepository.kt, MongoPlatformAccountRepository.kt, AuthTokenService.kt, MongoRefreshSessionRepository.kt, PlatformSessionAuthority.kt, AuthService.kt; plugins/Plugins.kt; auth/AccountLifecycleRoutesTest.kt and codec tests.
**Interfaces:** Produce AccountStatus and UserSummary.accountStatus/authRevision/institutionId. Native sessions bind to their account authRevision; institutional sessions retain existing provider authority semantics.
- [x] Write negative route tests for explicit suspended/revoked/malformed state, changed revision, pending basic-only access and malformed revision.
- [x] Run :services:api:test --tests '*AccountLifecycleRoutesTest'; expected assertion failures on current code.
- [x] Persist and project status/revision/institution; put identical claims in JWT and refresh snapshots; fail closed on native snapshot revision mismatch and suppress pending roles.
- [x] Run API/contracts/validation and Android JVM suites; expected all pass.
- [x] Commit the state/snapshot implementation and tests.

### Task 2: Atomic audited transitions and duplicate registration
**Files:** auth/AccountIdentityChange.kt, PlatformAccountRepository.kt, MongoPlatformAccountRepository.kt; auth/PlatformAccountLifecycleTest.kt, MongoAccountLifecycleTest.kt, MongoAuthFixture.kt.
**Interfaces:** Consume AccountStatus/authRevision; produce repository.changeIdentity(accountId, expectedRevision, change), returning an updated account on an atomic CAS or an exact idempotent retry; otherwise null. Internal persistence primitive, not a public approval service.
- [x] Write observable repository tests for CAS collision, exact retries, request ID payload mismatch, terminal revoked state, audit secrecy and concurrent normalized-email uniqueness.
- [x] Add only compiling interface stubs, run targeted tests; expected assertion failures.
- [x] Implement locked in-memory uniqueness and Mongo single-document CAS with embedded identity audit. Changes increment authRevision and include state, roles and institution in the same atomic write. Bound retained audit entries and document export/retention constraints.
- [x] Run API/contracts/Android suites, lint and Debug/Release; expected all pass.
- [x] Commit tests, implementation, migration/security audit and documentation.

### Task 3: Review and integration
**Files:** docs/security/V12_IDENTITY_LIFECYCLE_AUDIT_20261009.md and this plan; git-ignored execution ledger.
**Interfaces:** Consume immutable final branch diff and test evidence; produce own reviewed PR with exact-head green CI and safe squash merge.
- [ ] Fresh whole-branch security review under executing-plans; fix important findings with failing regression tests and a green full suite.
- [ ] Push own branch, create/attach own PR, inspect all CI on its exact head; expected green before squash.
- [ ] Merge own reviewed delivery and fetch latest main; verify primary branch/index/local changes preserved, then proceed to feat/v12-registration from latest main.

## Remaining V12 deliveries
Registration/email verification and tutor requests; scoped tutor approval; privileged MFA/bootstrap/admin UI; event-only guests/workshops; access experience; full release candidate are subsequent specified branches. This delivery alone does not claim V12 completion or production readiness.