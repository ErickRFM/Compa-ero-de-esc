# V12 universal registration implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans inline, task-by-task. Steps use checkbox syntax.

**Goal:** Extend existing native registration with verified email, pending teacher/tutor requests, external participant accounts and connected V8 onboarding.
**Architecture:** Keep AuthService/AuthRepository/SessionViewModel. Persist registration intent separately from granted roles in PlatformAccount. Email challenges and confirmation use the same document CAS so revocation and concurrent requests cannot grant authority. Public institutions list contains only real enabled directory records.
**Tech Stack:** Kotlin/Compose, shared serialization contracts, Ktor/Mongo, Java HTTP/JCE; existing themes/localization.
**Spec:** C:/Users/erik5/.codex/attachments/48d0fb68-1d64-4532-89e1-f726b07f8137/Texto pegado.txt. Base origin/main: 2d69bd7e72de05b60d4370e63ef206ed90af4bbc (PR137).

## Global Constraints
- No public ADMIN/SUPER_ADMIN or automatic TUTOR/TEACHER grant.
- Reuse RegistrationScreen/AuthV8Layout/shared components, login carousel and session refresh.
- Requested institution/group are preferences, not granted institution/group scope.
- Student registration works without an institution; teacher/tutor require an enabled platform institution and identity reference. No institution API integration is required.
- Persist only hash of a 256-bit email token, expire after one hour; consume once. No token or email provider key in logs/public responses.
- Email resend: at least 60 seconds between challenges and at most 3 per hour per account, enforced atomically in storage. Provider failure remains visible and account stays pending.
- Preserve primary checkout and all unrelated PRs/worktrees. Real production mail requires an operator-configured key and verified sender, never fabricated credentials.

## Review Focus
- Concurrent verification/resend versus suspension/revocation must not activate a stale identity.
- An unverified or pending tutor/teacher cannot use academic routes even with submitted preferences.
- Forged/unknown institutions and fields cannot confer tenant or administrative scope.
- Mail provider success means accepted for sending, not proof of mailbox delivery; failed/unknown outcome must be shown honestly.
- Pending sessions and forms must survive theme/language/large-text changes without publishing stale granted roles.

### Task 1: Persisted universal registration and email verification
**Files:** shared/contracts/AuthContracts.kt, UserRole.kt, UserSummary.kt, RegistrationContracts.kt; api/auth/PlatformAccountRepository.kt, MongoPlatformAccountRepository.kt, AuthService.kt, AuthRoutes.kt, AccountVerificationService.kt, RegistrationIntent.kt; api/institutions/InstitutionRepository.kt, InstitutionRoutes.kt; api/mail/VerificationEmailGateway.kt; api/application/IdentityFeatureGraph.kt/ApplicationModule.kt; api/config/ApiSettings.kt; api/plugins/Plugins.kt; auth JWT/refresh codecs; api/auth/UniversalRegistrationRoutesTest.kt, EmailVerificationRepositoryTest.kt and real-Mongo integration tests.
**Interfaces:** Produce RegistrationAccountType.TUTOR/PARTICIPANT, RegisterRequest.requestedInstitutionId/identityReference/preferredGroup, RegistrationIntent, InstitutionSummary and email confirmation/resend contracts. Produce repository.issueEmailVerification(accountId,expectedRevision,hash,now,expiresAt): Boolean and confirmEmailVerification(accountId,hash,now): PlatformAccount?; service issue(userId): VerificationDeliveryReceipt and verify(userId,token): PlatformAccount. Consume current authRevision CAS and create fresh native session after successful verification.
- [x] Write Ktor regressions for pending registration, rejected ADMIN, no granted tutor role/group, wrong/expired/consumed email token, duplicate normalized email and suspended verification. Add compiling interface stubs only where needed; run targeted tests and confirm assertion failures.
- [x] Implement four public profiles, strict bounded fields and actual institution directory validation; persist intent without privilege, issue hashed challenge and confirm atomically. Add provider adapter with idempotent sending and explicit unavailable/failed result, no public token disclosure.
- [x] Test concurrent confirmation/resend, cooldown/hourly limit, revocation collision and real Mongo behavior. Run API/contracts suites; all must pass, real-Mongo local skips disclosed.
- [x] Commit implementation/tests/minimal migration audit after green checks.

### Task 2: Connected registration and pending access UI
**Files:** feature/auth/RegistrationScreen.kt, AccountOnboardingScreen.kt, AuthRepository.kt, SessionViewModel.kt, RegistrationCopy.kt; app/MainActivity.kt; feature/auth tests; app/RegistrationLayoutUiTest.kt and onboarding instrumented tests.
**Interfaces:** Consume the shared RegisterRequest/InstitutionSummary/accountStatus/registrationAccountType/emailVerified and existing authenticated session. Produce AuthRepository.institutions/resendVerification/confirmVerification and SessionViewModel.loadRegistrationInstitutions/verifyEmail/resendVerification; confirmation persists the fresh LoginResponse through existing encrypted session storage. RegistrationScreen submits RegisterRequest with profile-relevant fields; MainActivity routes pending/participant accounts before academic experiences.
- [x] Write failing repository/ViewModel and UI tests for profile fields, pending-only routing, verification success/failure and no cached authority on logout. Run them before production changes.
- [x] Extend existing V8 form and locale copy, real institution selection, loading/error/empty states; connect pending verification/approval and basic participant account screen with actual repository/API actions.
- [x] Run Android JVM, app instrumentation API30, lint and Debug/Release; preserve screenshots through existing evidence exporter and leaveApksInstalledAfterRun option. Verify ES/EN, contrast, text scale and reduced motion.
- [x] Commit connected implementation/tests and actual evidence documentation.

### Task 3: Review and safe integration
**Files:** docs/security/V12_REGISTRATION_AUDIT_20261010.md, this plan and ignored execution ledger.
**Interfaces:** Produce one reviewed own PR with exact final-head green CI, then safe squash and latest-main verified tree.
- [ ] One fresh whole-branch security review; reproduce/correct important findings, then run full final checks.
- [ ] Push/create/attach own PR; require API real Mongo, Android JVM/lint/Debug/Release and API31/35 exact-head checks green before squash.
- [ ] Verify merged tree and preserved primary index; retain evidence and continue feat/v12-tutor-approval from latest main.

Remaining mandatory V12: approval and scoped assignments, MFA/bootstrap/admin console, guest workshops, full access experience and exact-main release candidate. This registration delivery alone does not claim all V12 complete or real production email delivery.