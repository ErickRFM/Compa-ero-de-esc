# Roadmap

## Foundation — complete on main

- Gradle monorepo and pinned toolchain
- shared contracts/models/validation
- Ktor API shell and provider boundary
- Android Compose shell
- design system, navigation and network foundations
- tested liveness/readiness behavior
- mock providers blocked from staging/production

## Product Foundation — merged to main

Merged in PR #3:

1. platform login and short-lived JWT;
2. encrypted Android token storage;
3. local JWT subject/expiry inspection;
4. observable session invalidation;
5. Room academic snapshot cache;
6. cache isolation by authenticated student id;
7. canonical `core:academic` repository;
8. Hoy contextual experience;
9. Agenda day/week experience;
10. profile/logout;
11. reusable academic UI components;
12. reproducible Android/API CI;
13. debug + minified release APK packaging gates.

## Immediate hardening

- login abuse limiting before public auth exposure;
- keep single-instance limiter explicit until shared gateway/storage exists;
- keep mocks locked out of staging/production;
- update release docs with exact CI evidence.

## External integration track

Before a real-user pilot:

- real pilot-school IdentityProvider;
- real pilot-school AcademicProvider;
- real school test accounts;
- real timetable validation against source-of-truth data.

These are external integration tasks, not reasons to block internal domain work.

## Academic model v2

Next domain refinement:

- terms;
- courses;
- groups;
- recurring schedule patterns;
- dated class occurrences;
- schedule overrides;
- cancelled/rescheduled/online states;
- room/teacher/time change metadata.

This model becomes the canonical base for attendance so attendance sessions bind to a real class occurrence instead of a loose course/group string.

## Attendance

Build in this order:

1. attendance domain and contracts;
2. teacher session lifecycle;
3. student attendance attempt;
4. idempotent record creation;
5. offline outbox/reconciliation;
6. signed dynamic QR evidence;
7. teacher roster/review.

Core state remains:

- VERIFIED
- LIKELY
- REVIEW_REQUIRED
- REJECTED

Location, Wi-Fi or BLE evidence is optional and requires a separate privacy/product decision.

## Due work and notices

After academic identity/schedule stability:

- tasks;
- due/overdue/completed states;
- announcements;
- calendar/events;
- LMS links/summary.

## Notifications

After attendance state transitions are stable:

- internal notification center;
- local reminders;
- server-generated events;
- optional push.

## Pilot

Pilot acceptance measures:

- median/p95 attendance time;
- VERIFIED rate;
- REVIEW_REQUIRED rate;
- false rejection rate;
- offline share;
- prevented duplicate retries;
- battery/camera failures;
- disputes;
- QR reuse/screenshot patterns.

## Later

- adaptive tablet layout;
- parent view;
- additional institutional adapters;
- optional proximity evidence;
- store-distribution hardening.

## Release rule

No phase is complete because code exists. It is complete when it is merged, built reproducibly, tests genuinely execute, error/offline states are covered and its acceptance scenario is demonstrated.
