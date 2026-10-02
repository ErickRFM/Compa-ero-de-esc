# ADR-001: Module boundaries

- **Status:** Accepted
- **Date:** 2026-09-30
- **Updated:** 2026-10-02
- **Scope:** Android Gradle modules and dependency direction

## Decision

Only modules with real production responsibility are declared in
`settings.gradle.kts`. Empty placeholder modules are not accepted.

The current product-foundation candidate contains these Android modules:

```
apps/android/
  app/
  core/
    academic/
    common/
    database/
    designsystem/
    navigation/
    network/
    security/
    testing/
    ui/
  feature/
    auth/
    home/
    schedule/
```

Every listed module contains code with a distinct reason to change.

## Why modules are created late

A module is created only when:

1. it has non-trivial production code;
2. it has a distinct responsibility;
3. its dependency direction is clear;
4. its name describes that responsibility;
5. its isolation is worth the Gradle/build cost.

This avoids architecture diagrams that look complete while containing empty
projects with no contract, owner or tests.

## Current core responsibilities

| Module | Responsibility |
|---|---|
| `core:common` | shared outcomes/errors/dispatching primitives |
| `core:designsystem` | Material theme, typography, shapes |
| `core:ui` | reusable student UI components and generic content states |
| `core:navigation` | destinations and app shell; never feature imports |
| `core:network` | Ktor client and safe error translation |
| `core:security` | encrypted platform token storage and local JWT claim inspection |
| `core:database` | Room cache primitives; never decides active identity |
| `core:academic` | canonical academic data source: session scope + API + Room fallback |
| `core:testing` | reusable deterministic test helpers |

`core:academic` was added in the product-foundation pass because Home and
Schedule previously owned different data paths. That created an invalid product
behavior: Schedule depended on Home having populated the cache first. The
canonical repository now owns that policy once.

## Deferred modules and triggers

| Module | Trigger |
|---|---|
| `core:datastore` | first durable non-security preference/sync cursor |
| `core:notifications` | first local or push notification |
| `core:location` | product need + accepted privacy model + actual location read |
| `feature:tasks` | real task contract, screen, state and tests |
| `feature:attendance` | attendance domain/session contract exists |
| other `feature:*` | real screen + ViewModel/domain responsibility |

Location is never created just because it is on a roadmap. See
[Location privacy](../privacy/LOCATION_PRIVACY.md).

## Dependency rules

1. Core modules never depend on feature modules.
2. Feature modules never depend on sibling feature modules.
3. `core:navigation` never imports feature screens.
4. `app` is the composition root and may know all Android modules.
5. Android never depends on `services:api`; wire types live in
   `shared:contracts`.
6. Institutional integration shapes never cross into Android.
7. `core:database` stores data but does not choose which user's data is valid.
   Identity scoping belongs in a higher data/domain layer such as
   `core:academic`.
8. A new dependency added only to bypass these rules is an architecture smell,
   not a shortcut.

## Android / API / institution boundary

```
Android features
      |
      v
core:academic
      |
      +---- core:security ---- active platform subject / token
      |
      +---- core:database ---- student-scoped cached snapshot
      |
      +---- core:network ----- HTTPS/JSON
                               |
                               v
                            Our API
                               |
                         provider interfaces
                               |
                               v
                         Institution systems
```

Rules:

- institutional credentials are only used inside the API/provider boundary;
- Android persists only the platform session token;
- the authenticated JWT subject scopes academic cache reads;
- remote academic responses must match the authenticated subject before they
  are accepted or cached;
- 401 invalidates the local platform session instead of serving stale data as
  though the account remained valid.

## Multi-user invariant

The same device may be used by different students.

Therefore:

```
active JWT sub = student-1
        |
        v
cache.read("student-1")
```

A cache row belonging to `student-2` is never a fallback candidate for
`student-1`.

This is both a privacy invariant and a correctness rule.

## Related

- [Android architecture](ANDROID_ARCHITECTURE.md)
- [System architecture](SYSTEM_ARCHITECTURE.md)
- [Master plan](../product/MASTER_PLAN.md)
- [Location privacy](../privacy/LOCATION_PRIVACY.md)
