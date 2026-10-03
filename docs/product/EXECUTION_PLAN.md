# Execution Plan — Compañero de Escuela

Status: **living execution document — F0 and internal F1 merged**
Baseline reviewed: `main` @ `61a01cb29592a988f97700772674637eedeb843d`
Date: 2026-10-02

## Rule of execution

The product evolves in this order:

`foundation -> identity/schedule -> attendance -> offline -> dynamic QR -> audit/review -> notifications -> pilot -> optional proximity signals`

A phase is not complete because code exists. It is complete only when its acceptance gate is demonstrated with tests and a reproducible build.

## F0 — Foundation closure

Goal: make `main` the single reliable base.

Work:
- diagnose GitHub Actions runs that failed before executing steps;
- rerun CI and compare with local clean build evidence;
- run clean build, tests, lint, debug/release packaging and API smoke checks;
- resolve cheap remaining warnings;
- merge `feat/foundation` only after evidence is consistent;
- keep local/development/staging/production configuration explicit;
- keep mock providers locked out of staging and production;
- establish signing/versioning for internal builds without committing secrets.

Gate:
> A clean clone of main builds backend and Android, runs tests/lint, produces an APK, and reports health/readiness coherently.

## F1 — Identity + “Today”

Student need: know what class is now, where it is, who teaches it, what comes next and whether something changed.

Modules:
- `core:security`
- `core:database`
- `core:datastore`
- `feature:auth`
- `feature:home`
- `feature:schedule`

Backend:
- authentication/session lifecycle;
- real `AcademicProvider` for the pilot school;
- academic load/schedule endpoints.

Data/domain:
- `SessionManager`
- `AccountRepository`
- `AcademicRepository`
- `SyncManager`

Gate M1:
> A student signs in on a real device, sees the real timetable, enables airplane mode, restarts the app and still sees the correct day. No institutional credential exists on the device.

## F2 — Attendance domain

Attendance is not a boolean. It is:

`Course -> Group -> Schedule -> Class occurrence -> AttendanceSession -> AttendanceAttempt -> AttendanceRecord`

Core states:
- `VERIFIED`
- `LIKELY`
- `REVIEW_REQUIRED`
- `REJECTED`

Minimum APIs:
- `POST /attendance/sessions`
- `GET /attendance/sessions/active`
- `POST /attendance/sessions/{id}/attempts`
- `POST /attendance/sessions/{id}/close`
- `GET /attendance/sessions/{id}/roster`
- `PATCH /attendance/records/{id}/review`

Student UX:
- show attendance action only when applicable;
- target normal flow under 10 seconds;
- show human status, never internal rule details.

Teacher UX:
- open/close session;
- live roster;
- verified/review/missing counts;
- resolve exceptions.

Gate M2:
> A teacher opens a valid session and an enrolled student records attendance in under 10 seconds. Duplicate submissions cannot create duplicate attendance.

## F3 — Offline attendance and idempotency

Use:
- Room;
- outbox/pending operations;
- `operationId`;
- WorkManager;
- retry/backoff;
- server timestamps;
- idempotent writes;
- reconciliation after connectivity returns.

The UI reads local state; network updates local state.

Gate:
> A registration survives network loss, process death and retry. The user can distinguish pending, synced, review and rejected states without seeing technical errors.

## F4 — Dynamic QR evidence

QR is evidence, not the attendance verdict.

Logical token:
- `sessionId`
- `nonce`
- `issuedAt`
- `expiresAt`
- key/version
- signature

Do not put name, student number, coordinates or `present=true` in the QR.

Rules:
- short validity;
- rotation within the attendance session;
- server-side validation;
- reject cross-session reuse;
- audit suspicious reuse without automatically treating every anomaly as misconduct.

Gate:
> Old screenshots expire, tokens cannot be reused outside their session and no invasive permission is required for the base flow.

## F5 — Explainability and review

Reason codes include:
- `QR_EXPIRED`
- `SESSION_CLOSED`
- `WRONG_SESSION`
- `DUPLICATE`
- `OFFLINE_LATE_SYNC`
- `NOT_ENROLLED`
- `OUTSIDE_ALLOWED_WINDOW`
- `TEACHER_REVIEW`

Audit:
- actor;
- session;
- device/server timestamps;
- state transition;
- operation id;
- reason;
- authorized reviewer.

Gate:
> Every disputed attendance can be reconstructed and explained without exposing a movement history.

## F6 — Notifications

Levels:
- N0: internal notification center;
- N1: local reminders;
- N2: server-generated events;
- N3: optional push.

Initial event types:
- `CLASS_STARTING`
- `ROOM_CHANGED`
- `ATTENDANCE_OPENED`
- `ATTENDANCE_CONFIRMED`
- `ATTENDANCE_REVIEW_REQUIRED`
- `ABSENCE_CONFIRMED`
- `INSTITUTIONAL_NOTICE`

Each event should carry a deep link to the related screen.

The domain emits `NotificationEvent`; delivery channels remain replaceable.

## F7 — School companion expansion

After timetable and attendance are stable:
- tasks/pending work;
- announcements;
- calendar/events;
- library;
- LMS links/summary;
- profile;
- attendance history.

The product integrates and summarizes; it does not replace SIS/LMS/library systems.

## F8 — Real pilot

Measure:
- median/p95 attendance time;
- VERIFIED rate;
- REVIEW_REQUIRED rate;
- false rejection rate;
- offline share;
- prevented duplicate retries;
- battery/camera failures;
- disputes;
- QR reuse/screenshot patterns.

Gate:
> Decide whether identity + schedule + dynamic QR + offline are sufficient before adding more permissions or hardware.

## F9 — Optional proximity evidence

Only if the pilot proves a concrete need:
- point-in-time Wi-Fi evidence;
- point-in-time location;
- BLE/beacon pilot.

Not allowed by default:
- background location;
- continuous Wi-Fi scans;
- geofencing;
- movement history;
- BLE rollout without pilot evidence.

## Git execution branches

Suggested sequence:

- `feat/identity-security`
- `feat/academic-provider`
- `feat/student-today`
- `feat/offline-academic-cache`
- `feat/attendance-domain`
- `feat/teacher-attendance-session`
- `feat/student-attendance`
- `feat/attendance-offline-sync`
- `feat/attendance-qr`
- `feat/attendance-review`
- `feat/notifications`

Every PR must include:
- one clear responsibility;
- tests;
- updated documentation when behavior/architecture changes;
- no secrets;
- reproducible build/lint/test evidence;
- acceptance criteria;
- merge only after feedback/checks are resolved.

## Initial execution board

| Priority | Work | Initial state |
|---|---|---|
| P0 | CI diagnosis / rerun | DONE |
| P0 | Foundation validation and merge | DONE |
| P1 | Identity/security platform slice | DONE |
| P1 | Home/Today + Agenda + offline timetable | DONE internally |
| P1 | Login abuse controls | ACTIVE / PR after squash cleanup |
| P1 | Real IdentityProvider | BLOCKED externally |
| P1 | Real AcademicProvider | BLOCKED externally |
| P1 | Academic model v2 | NEXT |
| P2 | Attendance domain | NEXT after academic model contract |
| P2 | Offline attendance | AFTER M2 basic |
| P2 | Dynamic QR | AFTER offline correctness |
| P3 | Notifications | AFTER attendance is stable |
| P3 | Pilot | AFTER real providers + MVP |

## Definition of done

A phase is done only when:
- merged to `main`;
- tests really executed;
- Android/backend build reproducibly;
- acceptance scenario is demonstrated;
- empty/error/offline states are handled;
- docs match behavior;
- no secrets/mocks leak into real environments;
- new permissions have documented purpose and privacy tradeoff.
