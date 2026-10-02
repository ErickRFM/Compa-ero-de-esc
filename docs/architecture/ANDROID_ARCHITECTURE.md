# Android architecture

Single activity, Kotlin, Jetpack Compose, Hilt, Material 3 and Room.

The product-foundation candidate is no longer a placeholder shell. It contains
real authentication, session lifecycle behavior, offline academic data, Hoy and
Agenda.

## Runtime flow

```
MainActivity
    |
    +-- SessionViewModel
    |      |
    |      +-- encrypted SessionTokenStore
    |      +-- SessionTokenInspector (sub / exp)
    |
    +-- authenticated shell
           |
           +-- Hoy
           +-- Agenda
           +-- Profile (secondary app-bar destination)
```

The bottom navigation intentionally contains only high-frequency destinations:
**Hoy** and **Agenda**. Profile is available from the top app bar instead of
using one third of permanent navigation.

## Academic data flow

```
HomeViewModel -----------+
                         |
ScheduleViewModel -------+--> core:academic AcademicRepository
                                  |
                    +-------------+-------------+
                    |                           |
              SessionTokenStore            Ktor API
                    |                           |
             JWT subject/expiry                 |
                    |                           v
                    +------> Room cache <--- accepted remote data
```

Important behavior:

- a usable token requires a parseable subject and non-expired `exp`;
- the active subject determines the only cache owner that may be read;
- remote academic data is rejected if its owner does not equal the JWT subject;
- non-auth network/server failures may fall back to that student's cache;
- a 401 clears the token and drives the global shell back to login;
- Agenda performs its own refresh/fallback and no longer depends on Home having
  run first.

## Module graph

```
                         app
                          |
      +-------------------+--------------------+
      |                   |                    |
 feature:auth        feature:home      feature:schedule
      |                   |                    |
      +----------+--------+---------+----------+
                 |                  |
            core:security      core:academic
                                      |
                        +-------------+-------------+
                        |             |             |
                   core:network  core:database  core:security
                        |
                   core:common

core:ui ----------> core:designsystem / core:common
core:navigation --> core:ui / core:designsystem
```

Feature modules never depend on one another.

## Session lifecycle

The real encrypted store exposes token changes as a Flow.

`SessionViewModel`:

1. observes the token;
2. parses `sub`, `display_name` and `exp`;
3. rejects malformed/expired values;
4. marks the shell authenticated while valid;
5. schedules local invalidation at expiry;
6. reacts immediately when another layer clears the token after a 401.

The client-side token parser is not a signature verifier. Authorization and JWT
signature verification remain server responsibilities.

## Offline policy

Room is a cache, not the system of record.

The current candidate stores an academic snapshot per owner id. A later
academic-model phase may normalize this into occurrences/tasks/etc., but no
feature is allowed to bypass the authenticated owner scope.

## UX shell

Shared UI primitives now include:

- academic class card;
- compact timeline item;
- human-readable status notice;
- generic loading/empty/error host.

Hoy prioritizes current class, next class and the day's compact timeline.

Agenda supports day/week reading and can refresh independently.

## Error handling

Ktor errors are translated before presentation:

```
network/server
   -> AppError
   -> Outcome
   -> feature state
   -> human copy
```

Technical details never become user-visible strings.

## Accessibility and adaptive work

Still required before pilot:

- Compose semantics tests;
- TalkBack review;
- large font scale;
- 48dp target audit;
- compact/medium/expanded layouts;
- tablet navigation rail;
- localization/resource extraction.

These remain release gates, not optional polish.

## Testing

Every Android test task must use JUnit Platform so JUnit 5 tests cannot compile
and silently execute zero tests.

Current candidate adds tests for:

- token subject/expiry inspection;
- usable token persistence;
- academic fallback scoped to the active user;
- 401 session invalidation;
- remote owner mismatch;
- weekly schedule ordering.

CI results must record **executed test counts**, not just a green Gradle exit.

## Related

- [ADR-001](ADR-001-MODULE-BOUNDARIES.md)
- [Master plan](../product/MASTER_PLAN.md)
- [Release readiness](../quality/RELEASE_READINESS.md)
