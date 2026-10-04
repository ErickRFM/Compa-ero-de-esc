# Release readiness

## Verdict

**Internal V5 candidate is integrated. Physical-device acceptance is required. Real-student pilot remains blocked by real institutional providers and release configuration.**

## Current baseline

Audited integration baseline before this audit-fix branch:

`main@c971ad8b5c51b4869189ee2cba2dae45a1addb43`

The merged product line includes:

- UPTLAX V5 design system and login;
- first-access activation via institutional identity validation;
- platform authentication and JWT issuance;
- encrypted Android session storage with keystore recovery;
- student Home/Hoy;
- Agenda day/week;
- profile/logout;
- student/teacher attendance;
- signed rotating attendance QR;
- offline attendance outbox + WorkManager reconciliation;
- role-aware attendance mode;
- Render development API as the debug default.

## CI evidence

Required workflows cover:

### API

- compile backend/shared;
- backend/shared tests;
- test-execution verification;
- runnable service assembly;
- smoke test;
- secret scan.

### Android

- compile Android sources;
- unit tests;
- test-execution verification;
- lint;
- debug APK;
- minified release APK;
- artifact validation;
- secret scan.

The current audit must not be considered closed until both API and Android workflows pass on its final commit.

## Auth / first access classification

The current **Activar acceso** flow is not a separate persistent registration database.

It validates:

```
matrícula / ID + institutional credential
 -> Companion API
 -> IdentityProvider
 -> roles + identity
 -> Companion JWT
```

This is intentionally the correct intermediate architecture while there is no persistent account-claim lifecycle.

## Runtime requirements for the deployed development API

For the current test deployment:

- `APP_ENV=development`;
- Mongo configured;
- JWT secret configured;
- `ATTENDANCE_QR_SECRET` required for QR issuance;
- mock providers are acceptable only because this is development.

Do not change this service to staging/production while mock providers are active; the API is designed to refuse that configuration.

## Release endpoint policy

Debug and release must not share an implicit backend.

- debug: `COMPANERO_API_BASE_URL`, defaults to the deployed development API;
- release: `COMPANERO_RELEASE_API_BASE_URL`;
- release falls back to an intentionally unreachable HTTPS URL unless explicitly configured.

A release APK is therefore not distribution-ready merely because it builds. A valid release endpoint must be supplied deliberately.

## Remaining blockers before a real UPTLAX pilot

| Area | Status |
|---|---|
| UPTLAX V5 UI | INTEGRATED |
| Login/session | INTEGRATED / physical retest required |
| First-access activation | INTEGRATED as provider validation |
| Student Home/Agenda | INTEGRATED |
| Attendance | INTEGRATED / physical E2E required |
| Teacher attendance | INTEGRATED |
| Teacher full academic workspace | PARTIAL |
| Coordinator/Admin/Super Admin workspace | NOT IMPLEMENTED |
| Real IdentityProvider | BLOCKED externally |
| Real AcademicProvider | BLOCKED externally |
| Real school accounts | BLOCKED externally |
| Notices/events/conferences provider | NOT IMPLEMENTED |
| Accessibility physical audit | REQUIRED |
| Release signing/version policy | REQUIRED |
| Privacy/legal review | REQUIRED |
| Production API endpoint | REQUIRED |
| Physical device acceptance | REQUIRED |

## Required physical acceptance

1. fresh install;
2. valid login;
3. invalid login message;
4. session survives process restart;
5. logout returns to login;
6. offline -> online recovery;
7. student Home/Agenda cache;
8. student QR scan;
9. teacher opens attendance;
10. QR rotation;
11. roster update;
12. verify/reject record;
13. teacher closes attendance;
14. background/foreground;
15. small phone + tablet;
16. TalkBack/focus/touch targets.

## Release classification

Current classification:

`INTERNAL_V5_READY_FOR_DEVICE_QA / REAL_PILOT_BLOCKED`

See also:

- [Integration audit](INTEGRATION_AUDIT_20261004.md)
- [Auth/session audit](AUTH_SESSION_ERROR_AUDIT.md)
- [Registration flow](../integrations/REGISTRATION_FLOW.md)
- [UPTLAX identity boundary](../integrations/UPTLAX_IDENTITY.md)
