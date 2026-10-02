# ADR-001: Module boundaries

- **Status:** Accepted
- **Date:** 2026-09-30
- **Scope:** `settings.gradle.kts` module list, dependency rules

## Context

The target architecture for Compañero de Escuela is a modular monorepo with
dedicated modules for every concern:

```
apps/android/
  app/
  core/  common, designsystem, ui, navigation, network,
         database, datastore, security, location, notifications, testing
  feature/  auth, home, attendance, scanner, schedule, subjects,
            announcements, calendar, tasks, library, events,
            campus, profile, settings
```

The first cut of `settings.gradle.kts` declared 25 Android modules. **None of
them contained a line of code.** They were placeholders created to match the
target diagram.

That state was a problem, and the reason is worth recording precisely rather
than as a general preference.

## Decision

**Only modules with real content are included in `settings.gradle.kts`.**
Seven Android modules are declared today, and every one contains production
code. The remaining target modules are documented here and created when they
take on real responsibility.

### Why `settings.gradle.kts` was reduced

1. **A declared module reads as finished.** In review, an empty module in the
   module list is indistinguishable from a complete one. The reader has to
   open it to learn that it is not. That is a review cost imposed on every
   future reader, in exchange for a diagram that already exists in this
   document.
2. **The build silently accommodated them.** Gradle configures a project with
   no build file without complaint. The build stayed green while twenty-five
   declared modules contributed nothing, so the lie was not caught by CI. It
   would have been caught only by a human noticing.
3. **A module with no code has no contract to test.** Once `core:database`
   exists, its boundaries, its transaction model and its migration story are
   all reviewable. Before that, its `build.gradle.kts` is a guess about
   dependencies that may turn out to be wrong.
4. **It invited a bigger, less honest first commit.** The alternative was to
   commit twenty-five empty modules, which reads as "architecture established"
   while establishing nothing.

### Why placeholder modules are not accepted

An empty module is not neutral. It is a maintenance surface with no owner, no
tests, and no way to be wrong. It also creates a real hazard: a future
developer adds a dependency to an existing empty module, wires it up, and ships
a feature into a structure nobody validated.

The rule here is narrower and stronger than "avoid clutter": **a module is
created when something needs to be put in it.**

### Conditions for creating a module

All of the following must be true. Any one of them alone is not enough.

1. **It has code.** A concrete, non-trivial amount, not a stub.
2. **It has a distinct reason to change.** Something that would be edited for
   a different reason, or on a different schedule, than its neighbours. If a
   module would only ever change together with `core:ui`, it belongs in
   `core:ui`.
3. **Its dependency list is stable.** A module whose dependencies change every
   few weeks is not paying for its isolation.
4. **It can be named honestly.** `core:network` says what it is. A module
   called `core:utils` does not, and cannot be.
5. **It is worth its build cost.** Each module adds configuration and
   incremental-build overhead. That is cheap at seven and noticeable at
   thirty.

When a module is created, this ADR is updated in the same commit.

### Deferred modules and their trigger

| Module | Created when |
|---|---|
| `core:database` | There is a first entity to persist and its schema is stable |
| `core:datastore` | There is a first non-trivial preference (session, sync cursor) |
| `core:security` | The first credential is stored, or signing is needed |
| `core:location` | Location is actually read, and the privacy model is agreed |
| `core:notifications` | There is a first local or push notification |
| `feature:*` | Each is created with its screen, its ViewModel and its tests |

`core:location` carries an extra condition: it is not created merely because
location is planned. See
[Location privacy](../privacy/LOCATION_PRIVACY.md) for the questions that must
be answered first.

## Dependency rules

These are enforced by convention and reviewed, not by a tool.

1. **Dependencies point in one direction.** Never upward, never sideways
   between siblings.
2. **`core:common` and `core:designsystem` depend on nothing.** They are the
   floor, and a cycle here would be unfixable.
3. **`core:navigation` never references a feature.** It takes its graph from
   the caller. This is the rule most likely to be broken by accident, because
   importing a screen looks helpful and the compiler will not object.
4. **Features depend on core, never the reverse.**
5. **`app` is the only module allowed to know every other module.** It is the
   composition root. A feature that needs a second feature is a sign the
   shared code belongs in `core:ui` or `core:common`.
6. **A feature never depends on `services/api`.** The contract in
   `shared:contracts` is the only shared surface, so a wire change cannot
   silently couple a screen to a server implementation.
7. **No `implementation`-scope shortcut to dodge a rule.** If a module needs a
   transitive dependency, that is a signal the rule above it is wrong.

## Boundaries between Android, backend and external systems

```
┌──────────────┐   HTTPS + JSON    ┌──────────────┐   provider iface   ┌───────────────┐
│   Android    │ ────────────────▶ │  Our API     │ ─────────────────▶ │  Institution  │
│              │                   │              │                    │               │
│ shared:      │                   │  holds all   │                    │  credentials  │
│ contracts    │◀──────────────────│  credentials │                    │  live only    │
└──────────────┘                   └──────────────┘                    └───────────────┘
```

| Boundary | Rule |
|---|---|
| Android → API | Only `shared:contracts` types cross. No client-side knowledge of upstream shape. |
| API → Institution | Only through a provider interface. Never a raw HTTP call from a route. |
| Adapter → Domain | Only inside a mapper. The single place upstream vocabulary is allowed. |
| Domain → Wire | `ApiResponse` and friends. No internal type is serialized by accident. |

The single most important rule: **institutional credentials never leave the
API.** A student app holding a school-system password is a credential leak
waiting for a rooted phone.

## Consequences

### Accepted costs

- The module list is shorter than the target diagram, so a reader comparing
  this ADR to a roadmap has to look twice. Mitigated by the tables above.
- Module creation becomes a deliberate act requiring justification in review.
  Accepted: it happens a handful of times, not constantly.
- Convention plugins were considered and deferred. With seven modules the
  duplication is tolerable; past roughly ten it will not be, and that is the
  trigger to revisit.

### Benefits

- Every module in `settings.gradle.kts` is a module someone can review.
- The build is green because it works, not because it is configured around
  things that do not exist.
- Adding a module later is additive and honest.

## Related

- [System architecture](SYSTEM_ARCHITECTURE.md)
- [Android architecture](ANDROID_ARCHITECTURE.md)
- [Integration architecture](../integrations/INTEGRATION_ARCHITECTURE.md)
