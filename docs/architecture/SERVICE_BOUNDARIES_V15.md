# Service Boundaries V15

## Objective

Keep Compañero de Clase modular by ownership instead of by convenience. The app shell composes
screens; it must not own feature repositories or business rules. The API composition root builds
feature graphs; it must not implement feature-specific persistence selection or authorization rules.

## Backend ownership

| Domain | Owns | Must not own |
| --- | --- | --- |
| auth | JWT principal parsing, role gates, sessions, platform accounts | attendance/classroom policy |
| presence | school-day presence, entry QR lifecycle, school-network validation | class attendance decisions |
| attendance/session | open/close class pass, teacher/admin session views, roster | student enrollment loading |
| attendance/student | enrollment gate, active pass discovery, QR evidence, attendance attempts | teacher review decisions |
| attendance/review | manual teacher/admin disposition and evidence review | QR signing |
| attendance/qr | signed per-class QR issuance/verification | school entry QR lifecycle |
| academic | groups, schedules, academic reconciliation | classroom enrollment tokens |
| classroom | canonical class assignment and enrollment | institutional schedule parsing |
| channel | controlled class/institutional communication | class ownership |
| events | notices/cancellations/reschedules | authentication |
| excuses | tutor/admin excuse workflow | attendance evidence mutation |
| devices | device token registry | notification delivery policy |

## API composition

`ApplicationModule.kt` is only the composition root.

Feature-specific construction belongs to:

- `IdentityFeatureGraph`
- `AcademicFeatureGraph`
- `ClassroomFeatureGraph`
- `PresenceFeatureGraph`
- `AttendanceFeatureGraph`

Rules:

1. Mongo/in-memory selection belongs to a feature graph.
2. Route authorization uses `PlatformPrincipal.kt`; routes must not duplicate JWT parsing.
3. Services may depend on repository interfaces, policies and explicit collaborating services.
4. A service must represent one use-case family, not an entire product area.
5. Shared contracts contain transport models only; repository implementation details stay server-side.

## Attendance boundaries

School entry and class attendance remain two separate filters:

`SchoolEntryQr + authorized Wi-Fi -> SchoolPresence -> class pass -> attendance attempt`

Class attendance is separated into:

- `AttendanceSessionService`: teacher/admin pass lifecycle and roster.
- `AttendanceStudentService`: student discovery, QR evidence and attempt registration.
- `AttendanceReviewService`: teacher/admin corrections and final disposition.
- `AttendanceQrService`: signed rotating class QR only.
- `AttendanceAccessPolicy`: common ownership rule.
- `AttendanceEnrollmentResolver`: academic enrollment lookup and integration error mapping.

Evidence and teacher disposition are intentionally separate. A teacher may mark a student
`PRESENT` while the original technical evidence remains `REJECTED` or `REVIEW_REQUIRED`.

## Presence boundaries

- `SchoolPresenceService`: starts/reads/closes the school-day session.
- `SchoolEntryQrService`: admin lifecycle for institutional entry QR resources.
- `SchoolEntryQrVerifier`: verifies managed or legacy QR evidence.
- `SchoolNetworkVerifier`: SSID/BSSID policy only.

No Wi-Fi matching or QR hashing should be reintroduced into `SchoolPresenceService`.

## Android ownership

`apps/android/app` is the shell:

- application setup
- navigation composition
- DI that truly spans multiple features
- debug-only catalog entry points

Feature ownership after V15:

- `feature:home`: student/teacher home plus admin/coordinator home surfaces.
- `feature:attendance`: attendance screens and ViewModel.
- `feature:classroom`: class/group repositories, ViewModel and UI.
- `feature:profile`: profile UI and active-experience preferences.
- `feature:settings`: appearance/integration settings.
- `feature:schedule`, `feature:auth`, `feature:channel`: existing feature ownership.

A production screen/repository/ViewModel must not be added under
`apps/android/app/src/main/.../feature`.

## Anti-orphan checklist

Every production resource must satisfy all applicable checks:

- registered in `settings.gradle.kts` if it is a Gradle module;
- depended on by its consumer module;
- reachable from navigation or explicitly documented as background-only;
- repository has a service/ViewModel consumer;
- route has a registered composition-root call;
- service has a route, worker or another explicit consumer;
- contract is referenced by at least one producer and one consumer when bidirectional;
- Mongo collection has a repository owner;
- no duplicate role/JWT parsing outside auth helpers;
- tests cover each critical gate and manual override;
- removed implementations are deleted, not left as unused fallback code.

## Findings fixed in V15

1. `/attendance/sessions/open` was declared in the unauthenticated route branch while requiring a principal.
2. Attendance route and presence route duplicated JWT/role parsing.
3. Device routes duplicated JWT principal parsing.
4. `SchoolPresenceService` mixed QR hashing, managed QR lookup, Wi-Fi policy and session lifecycle.
5. `AttendanceService` mixed teacher sessions, student attempts, QR inspection and review.
6. Android `app` owned complete admin/coordinator/classroom/profile/settings features.
7. Classroom Hilt bindings lived in the app shell instead of the classroom feature.

## Remaining rule

Before merging V15, CI must prove API compile/tests/smoke and Android compile/tests/lint/package.
No merge should bypass those checks.
