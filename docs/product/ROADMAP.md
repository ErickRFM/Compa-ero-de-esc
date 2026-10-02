# Roadmap

## Foundation — complete on main

- Gradle monorepo and pinned toolchain
- shared contracts/models/validation
- Ktor API shell and provider boundary
- Android Compose shell
- design system, navigation and network foundations
- tested liveness/readiness behavior
- mock providers blocked from staging/production

## Product Foundation — active candidate

Branch: `feat/product-foundation-v2`

Implemented in the candidate:

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
11. reusable academic UI components.

External blockers before a real-user pilot:

- real pilot-school IdentityProvider;
- real pilot-school AcademicProvider;
- working GitHub Actions execution or equivalent reproducible verification.

## Next — Academic model v2

- terms, courses and groups;
- recurring schedule patterns;
- dated class occurrences;
- schedule overrides;
- cancelled/rescheduled/online states;
- room/teacher/time change metadata.

## Then — Due work and notices

- tasks;
- due/overdue/completed states;
- announcements;
- calendar/events;
- LMS links/summary.

## Then — Attendance

Build in this order:

1. attendance domain;
2. teacher session lifecycle;
3. student attendance attempt;
4. idempotency;
5. offline outbox/reconciliation;
6. signed dynamic QR evidence;
7. teacher roster/review.

Location, Wi-Fi or BLE evidence is optional and requires a separate privacy and
product decision.

## Later

- adaptive tablet layout;
- notification center and local reminders;
- optional push;
- parent view;
- additional institutional adapters;
- real pilot and store-distribution work.

## Release rule

No phase is complete because code exists. It is complete when it is merged,
built reproducibly, tests genuinely execute, accessibility/offline/error
states are covered and the acceptance scenario is demonstrated.
