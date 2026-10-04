# Integration audit — 2026-10-04

Baseline audited: `main@c971ad8b5c51b4869189ee2cba2dae45a1addb43`

## Executive verdict

**INTERNALLY INTEGRATED / PHYSICAL PILOT REQUIRED / REAL-INSTITUTION PILOT BLOCKED**

The repository is structurally coherent and the merged product line is buildable. Auth, session storage, student academic surfaces, attendance, UPTLAX V5 branding, teacher attendance mode, Render API wiring and first-access activation are now in one mainline.

This is not yet a real-school production release. The remaining blockers are mostly product/institutional rather than basic build integrity.

## What was verified

### Repository and CI

- one open integration line was merged before this audit;
- no open pull requests remained at audit start;
- Gradle modules are explicit and responsibilities are separated;
- API CI on the audited main commit passed;
- Android compile, unit-test execution verification and lint passed on audited main;
- debug APK packaging passed while the audit was running;
- release packaging is part of the required CI gate;
- committed-secret scanning is part of CI.

### Android architecture

- single-activity Compose shell;
- Hilt dependency injection;
- feature modules: auth, home, schedule, attendance;
- core modules: academic, attendance, common, database, designsystem, motion, UI, navigation, network, security, testing;
- shared wire/domain modules remain outside Android;
- session JWT is stored encrypted using Android Keystore + DataStore;
- stale/corrupt keystore state is recovered as signed-out state;
- known secure-storage failures are classified instead of becoming generic unknown failures.

### Auth / first access

Current first-access semantics are internally consistent:

```
Login
 -> Activar acceso
 -> matrícula / ID + institutional credential
 -> Companion API /auth/login
 -> IdentityProvider
 -> Companion JWT
 -> role-aware product
```

This is **activation/validation**, not yet a persistent account-claim lifecycle. The UI must continue to describe it that way until a persistent account model exists.

### Role behavior

- Student: Home + Agenda + Attendance + Profile.
- Teacher-only: Attendance as the top-level work surface + Profile from the app bar.
- Attendance itself selects student/teacher mode from JWT roles.
- Unsupported roles do not silently receive student attendance authority.

Current product gap: Coordinator/Admin/Super Admin do not yet have dedicated operational surfaces. They authenticate as valid platform roles but the Android shell is not a complete admin product.

### Attendance

Implemented and integrated:

- teacher occurrence list;
- open/close attendance session;
- signed rotating QR;
- student scan attempt;
- offline local outbox;
- WorkManager reconciliation;
- roster;
- teacher verification/rejection;
- Mongo-backed repository outside local development.

Runtime dependency: `ATTENDANCE_QR_SECRET` must be configured for QR issuance.

### Backend boundaries

- Android calls Companion API, not the school directly;
- institution access is behind provider interfaces;
- production/staging boot refuses mock providers;
- Mongo lifecycle is behind a connection abstraction;
- JWT issuer/audience/secret are configuration-driven;
- attendance persistence requires Mongo outside local development.

## Findings fixed by this audit

### IA-001 — Release APK could inherit development Render backend

**Severity: High**

Before this audit, debug and release both used `COMPANERO_API_BASE_URL`, whose default is the public Render development API. A minified release APK could therefore be distributed while still talking to a development backend with mock providers.

Fix:

- debug uses `COMPANERO_API_BASE_URL` and defaults to the Render development service;
- release uses `COMPANERO_RELEASE_API_BASE_URL`;
- release defaults to `https://invalid.invalid/` so an accidentally distributed build cannot silently call development;
- release endpoint must be HTTPS and end in `/`.

### IA-002 — UPTLAX logo had two sources of truth

**Severity: Medium**

The logo existed in both `core:designsystem` and `feature:auth`, with different path data.

Fix:

- Login now consumes `UptlaxBrand` from the design system;
- the auth-local drawable is removed;
- `core:designsystem` is the canonical branding source.

### IA-003 — Release-readiness documentation was stale

**Severity: Medium**

The readiness document still described attendance as a future phase and referenced an old main baseline.

Fix:

- release/readiness documentation is reconciled with V5, attendance, first-access activation and current blockers.

### IA-004 — main branch has no protection/rules gate

**Severity: High**  
**Status: Open / repository administration required**

GitHub reports `main` as unprotected with no required status checks. The CI workflows are strong, but they are advisory if a direct push can bypass them.

Required repository policy:

- require pull requests before merging;
- require `ci-api` and `ci-android`;
- require branches to be up to date before merge;
- block force-pushes to `main`;
- block deletion of `main`.

## Remaining blockers

### Product / role scope

- dedicated Coordinator UI;
- dedicated Admin UI;
- dedicated Super Admin UI;
- teacher Home/Agenda/Notices/Meetings beyond attendance;
- institutional announcements/events/conferences integration.

### Real-school integration

- real UPTLAX IdentityProvider;
- real AcademicProvider;
- school-owned test accounts;
- provider mapping for real institutional roles;
- persistent Companion account claim lifecycle if required by product policy.

### Runtime / deployment

Before attendance QR physical testing:

- verify `ATTENDANCE_QR_SECRET` is present in the deployed API;
- verify `/health` and `/ready`;
- verify Mongo indexes/persistence under real attendance traffic;
- confirm the Render service is intentionally `development` while mocks are used.

### Physical acceptance

Required before pilot classification changes:

- login and session restore on a real Android device;
- logout;
- process kill/restart;
- offline -> online recovery;
- student QR scan;
- teacher open/rotate/close;
- app background/foreground;
- small phone and tablet layouts;
- TalkBack/focus/contrast/touch targets;
- release-signed APK policy.

## Architecture verdict

The current modular monolith is appropriate for this stage. No framework rewrite is justified.

Keep:

- Ktor backend;
- native Android/Compose;
- provider boundaries;
- shared contracts;
- Room/DataStore/Keystore separation;
- feature/core module split.

Do not add another framework or parallel identity system to solve the remaining gaps.
