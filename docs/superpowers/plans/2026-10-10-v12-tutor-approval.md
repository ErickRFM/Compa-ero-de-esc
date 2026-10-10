# V12 tutor approval and institutional scope implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans inline, task-by-task. Steps use checkbox syntax.

**Goal:** Make verified teacher/tutor applications administratively actionable with real MFA, bounded institution/group/period grants and connected existing Android administration.
**Architecture:** Extend PlatformAccountRepository CAS and TutorAssignmentService. Account applications remain separate from roles; tutor access additionally requires a live valid persisted assignment. Shared native actor resolution and institution policy constrain existing administration, preserving representatives and private tutor cases. A real encrypted TOTP/step-up service is a prerequisite, not a simulated verifier.
**Tech Stack:** Kotlin/Ktor/Mongo single-document CAS, JCE HMAC/AES-GCM, Compose/Hilt/StateFlow and existing HttpClient/session refresh/shared V8.
**Spec:** C:/Users/erik5/.codex/attachments/48d0fb68-1d64-4532-89e1-f726b07f8137/Texto pegado.txt.
**Base:** origin/main ff9897eae3c5c0d6129bb7800e65af9b36ed24cc, verified squash of PR138. This is the owned isolated tutor approval delivery.

## Global Constraints
- Preserve C:/proyectos/esc and every unrelated worktree/PR; own managed worktree and feat/v12-tutor-approval branch only.
- No public ADMIN/SUPER_ADMIN grant; teacher/tutor roles only approved verified applications, never requested UI preferences.
- Group authority includes actual institution, period, start and end; unknown legacy tenant remains denied pending explicit protected migration.
- TUTOR+TEACHER retains TEACHER; role generations prevent revoked/regranted TUTOR reviving old assignments.
- Single-document account approval CAS, deterministic assignment creation and exact-scope retry; no claim of Mongo multi-document atomic transactions.
- MFA has a unique random factor per identity, encrypted with operator-supplied 32-byte key, confirmed OTP and replay CAS. Missing key returns unavailable; no production bypass.
- Step-up proofs bind actor, current authRevision, action, resource and operation, expire after five minutes and cannot authorize another payload. No passwords, OTPs, seed/proof tokens in logs/audit/toString/saved state.
- Native actor resolution validates persisted session source NATIVE and live account. Institutional/native identifiers are never inferred interchangeably.
- Existing representatives/classrooms/schedules/channel/QR/PDF/tutoring remain; institutional ADMIN cannot use global role shortcuts to cross tenants. SUPER_ADMIN still requires real tutor assignment for private case notes.
- UI uses actual backend data, existing themes/locales/resources/repositories/navigation. No hardcoded metrics or disconnected actions.

## Review Focus
- Target and actor revocation during approval, duplicate application approval and changed-scope retry.
- Tutor grant revoke/regrant plus period expiry, inactive group, mismatched institution and old assignment rows.
- Native versus institutional session identity collision and old JWT/refresh following authority change.
- Concurrent OTP consumption, encrypted-factor substitution, wrong proof/action/payload, missing production key.
- Older global ADMIN shortcuts in representatives, classrooms, schedules, channel, attendance, presence and excuses; scoped live data must be validated before privileged admins are provisioned.

### Task 1: Real native MFA and scoped step-up
**Files:** shared/contracts/PrivilegeSecurityContracts.kt; api/security/Totp.kt, MfaSecretCipher.kt, MfaRepository.kt, MongoMfaRepository.kt, MfaService.kt, MfaRoutes.kt, NativeActorResolver.kt; config/ApiSettings.kt; application/IdentityFeatureGraph.kt, ApplicationModule.kt; plugins/Plugins.kt; api/security/*Test.kt; .github/workflows/ci-api.yml.
**Interfaces:** Produce MfaService.beginEnrollment(actor,password), confirmEnrollment(actor,enrollmentId,otp), issueStepUp(actor,password,otp,action,resourceId,operationId,payloadDigest), requireStepUp(actor,proof,action,resourceId,operationId,payloadDigest). Actor is a live native PlatformAccount resolved from the persisted refresh session. MfaRepository uses factor version/timestep CAS, encrypted factor bytes and bounded/expiring proof rows; Mongo and in-memory behavior match. Shared response secret/proof toString redacted; bodies bounded before JSON parsing. Config mfaEncryptionKey is optional and never logged; no key makes operations fail closed.
- [x] Write compiling stubs and failing published RFC6238 vectors, encrypted AAD swap, expired/unconfirmed enrollment, password mismatch, OTP replay/concurrency, scope/proof mismatch, actor revision and missing-key tests; run targeted API security tests and inspect assertion RED.
- [x] Implement 30-second TOTP, random individual secrets, AES-GCM with actor/factor AAD, ten-minute pending enrollment, bounded password reauthentication, persisted consumed timestep and hashed five-minute step-up proof. Reject active-factor replacement; retain existing factor across authority changes while proofs expire on revision change.
- [x] Connect authenticated /auth/mfa/status, /enrollment, /confirmation, /step-up routes and request rate limits using existing auth/session graph. Add actual Ktor workflows and real Mongo CAS integration, explicitly require these tests non-skipped in CI.
- [x] Run API/shared full suite; commit only explicit owned files after green. Document that test MFA proves software flow, not production operator enrollment or phishing resistance.

### Task 2: Scoped staff applications and existing academic authorization
**Files:** shared/contracts/AccountAdministrationContracts.kt, AcademicGroupContracts.kt, TutoringContracts.kt; api/auth/PlatformAccountRepository.kt, MongoPlatformAccountRepository.kt, AccountIdentityChange.kt, RegistrationIntent.kt; api/admin/AccountAdministrationService.kt, AccountAdministrationRoutes.kt, InstitutionScopePolicy.kt; academic/groups/*, tutoring/TutorAssignmentService.kt, TutorAssignmentRepository.kt; representatives/GroupRepresentativeService.kt, GroupRepresentativeRoutes.kt; existing academic resource services/routes/policies; application/AcademicFeatureGraph.kt, ApplicationModule.kt; corresponding API tests and real Mongo tests.
**Interfaces:** PlatformAccount gains separate bounded roleApplications and per-role grant generations. Request application CAS validates current account revision without granting a role; approval uses existing identity CAS extended with exact scope digest and application decision. Service listPending/listUsers/metrics reads actual scoped repository data; requestTutor validates verified active identity and requested real institution; approveStaff derives TEACHER/TUTOR only from persisted application. TutorAssignment records carry institutionId, periodId, startsAt, endsAt and tutor role generation; group metadata is explicitly administratively assigned, never guessed. Existing TutorAssignmentService remains the access gateway.
- [ ] Write RED for tutor self-assignment, pending teacher, ADMIN-to-SUPER_ADMIN, cross-institution approval/list/revoke, multi-role teacher+tutor, duplicate approval/different scope retry, tutor wrong group/period/expiry/generation and revoked actor. Add representatives overview authorization regression while retaining its accepted appointment lifecycle.
- [ ] Implement live native administrator capability/tenant checks and actual institution/group validation. Approval changes target application/state/role/audit atomically, then deterministic tutor assignments; retry resumes only original actor/scope and current target grant generation. No actual assignment means no tutorated data access.
- [ ] Extend scoped group creation/member linking and existing assignment creation/revoke/list. Require real current teacher identity on classroom assignment. Harden existing ADMIN shortcuts and preserve academic owner/member checks, private tutor note isolation and honest provider-unavailable states.
- [ ] Add protected explicit migration for unknown legacy group tenant/period metadata with step-up and audit; do not silently seed institutional or user data. Test old rows deny scoped privileges before migration and exact migration retry is idempotent.
- [ ] Full API/shared green, real Mongo approval/assignment race tests represented in CI; commit explicit implementation/tests/migration documentation.

### Task 3: Connected Android approval, tutor request and MFA controls
**Files:** feature/home/.../feature/admin/AdminHomeScreen.kt, AdminRepository.kt, AdminViewModel.kt, AdminUiState.kt and existing module dependencies/resources; feature/auth/MfaRepository.kt, MfaViewModel.kt, MfaScreen.kt and auth resources; feature/profile/src/main/kotlin/org/companerodeescuela/feature/profile/ProfileScreen.kt; core/navigation/Destination.kt; app/MainActivity.kt; Android repository/ViewModel tests and app/V12AdministrationUiTest.kt.
**Interfaces:** Reuse injected HttpClient, SessionRefreshCoordinator, SessionTokenStore and apiCall. AdminHomeScreen consumes real state/users/pending/groups/assignments/metrics and invokes actual service actions; MfaScreen enrollment/confirmation/step-up uses typed contracts and memory-only secrets. A current verified teacher can submit a supplementary tutor application from actual profile/navigation; the response never sets client-granted roles. Approval invalidates the target old JWT/refresh; pending check handles unauthorized state through existing session teardown and fresh login.
- [ ] Write RED repository/ViewModel tests for all connected actions, true unavailable/error/pending state, denied cross-tenant operation and stale session handling. Actual UI tests exercise navigation, MFA confirmation/proof use and tutor request without granting permissions locally.
- [ ] Implement existing admin screen with localized shared V8 surfaces and real loading/empty/error/permission states, preserving existing academic links. Bind requested groups/period/validity to backend data and stable operation IDs; require real step-up for critical actions.
- [ ] Connect MFA setup/confirmation and active teacher tutor-request routes in MainActivity, preserve six-experience navigation/locales/themes/text/motion; no orphan screen, saved OTP or fake success counter.
- [ ] Run affected Android JVM/API30 UI, capture actual connected screen with test backend data clearly identified, lint/build supplied by exact-head CI. Commit explicit files and minimal evidence doc.

### Task 4: One security review and exact-head integration
**Files:** docs/security/V12_TUTOR_APPROVAL_AUDIT_20261010.md, plan and own ignored ledger.
**Interfaces:** One fresh whole-branch review; exact-head CI all green; own attached PR squash; merged tree and primary branch/index preserved; next provisioning delivery latest origin/main.
- [ ] Generate review package from actual initial merge-base and whole branch; dispatch one fresh strongest available reviewer with spec/plan/ledger/review focus. Regrade every finding; ONE TDD fix pass for Critical/Important, ledger declined judgments/minors.
- [ ] Push/create/attach own PR, inspect exact head/base and every API/Android/API31/35 check. Correct failures; do not bypass protected branch or merge an outdated head.
- [ ] Authorized squash after green/security gate, fetch verified merge/tree, retain proof artifacts and audit ledger before archiving this owned worktree. Continue provisioning, workshops, access and full final-main release candidate.
