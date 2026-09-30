# Roadmap

## Foundation — complete

Establish the technical foundation so that every later phase is additive.

- Gradle 8.14.3 monorepo with a version catalog
- Shared pure-Kotlin modules: contracts, models, validation
- Ktor 3 API with configuration, logging, health, readiness, version
- Institutional provider boundary with mappers and mocks
- Mocks locked out of staging and production
- Android foundation: Compose shell, design system, navigation, network layer
- 73 distinct tests (84 executions), CI workflows, architecture and security documentation

**What "complete" means here:** the build is green, the service starts, the
test suite genuinely executes, and the integration boundary is real. No
product feature is implemented, and that is the point.

## Phase 1 — Identity and academic data

The first vertical slice: a student signs in and sees their real timetable.

1. `core:security` — credential storage, the first module created under
   [ADR-001](../architecture/ADR-001-MODULE-BOUNDARIES.md)
2. API authentication, JWT, session lifecycle
3. `AcademicProvider` real implementation for the first pilot school
4. `GET /academic/load`, `GET /academic/schedule`
5. `core:database` — cache the academic snapshot for offline
6. Android: `feature:auth`, `feature:home`, `feature:schedule`
7. Offline read of the cached timetable

Exit criteria: a student signs in on a real device, sees their real timetable
with the network off, and no institutional credential exists on the device.

## Phase 2 — Attendance

1. `core:location` — created only after the privacy model is agreed
2. Attendance endpoints
3. `feature:attendance`
4. Offline submission with queued sync and conflict resolution
5. Teacher roster view

Exit criteria: attendance is recorded in under ten seconds, works offline, and
a location sample is stored only for as long as the policy allows.

## Phase 3 — Notifications and the rest of the day

1. `core:notifications`
2. `feature:announcements`, `feature:tasks`, `feature:library`, `feature:events`
3. `core:datastore` — sync cursor, preferences
4. Deeper offline sync

## Later

- Real academic providers for more than one school, which is the test of
  whether the provider boundary actually holds
- Parent view
- Tablet layout

## Explicitly deferred

Not planned, and not to be started without a decision record:

- QR attendance codes and BLE beacons. High privacy cost, and the value over
  a location-based flow is unproven.
- FCM push. Requires a Google dependency the product may not want.
- Automated publishing to app stores.
- Any analytics or crash-reporting service.

## Risks that could change this order

| Risk | Effect |
|---|---|
| A pilot school has no usable API | Phase 1 slips; expect file-import workarounds |
| Location permission is refused at scale | Phase 2 needs a non-GPS fallback |
| Institutional systems are too inconsistent to normalize | The provider boundary itself needs revisiting |

## Related

- [Product vision](PRODUCT_VISION.md)
- [ADR-001: module boundaries](../architecture/ADR-001-MODULE-BOUNDARIES.md)
- [Release readiness](../quality/RELEASE_READINESS.md)
