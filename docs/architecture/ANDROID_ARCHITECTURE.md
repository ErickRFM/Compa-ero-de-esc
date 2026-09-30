# Android architecture

Single activity, Jetpack Compose, Hilt, Material 3. The app is a shell at
this stage: the architecture is real, the screens are placeholders.

## Module graph

Dependencies point in one direction only. There is no arrow going back up.

```
                    apps:android:app
                            │
        ┌───────────┬───────┼────────┐
        ▼           ▼       ▼        ▼
  core:design  core:ui  core:nav  core:network
        ▲           ▲       ▲        │
        └───────────┴───────┘        │
                            │        ▼
                       core:common ──┘

  core:testing ──▶ core:common
  shared:contracts ──▶ core:network
```

| Module | Owns | May depend on |
|---|---|---|
| `core:common` | `Outcome`, `AppError`, `Dispatchers` | nothing |
| `core:designsystem` | Theme, type scale, shapes | nothing |
| `core:ui` | `ContentState`, shared composables | `core:common`, `core:designsystem` |
| `core:navigation` | Destinations, bottom bar, nav shell | `core:designsystem`, `core:ui` |
| `core:network` | Ktor client, `Outcome` translation | `core:common`, `shared:contracts` |
| `core:testing` | `TestDispatcherProvider` | `core:common` |
| `app` | Wiring, DI, screens, manifest | all of the above |

`core:common` and `core:designsystem` depend on nothing. That is deliberate: it
is what makes them safe to use from anywhere without creating a cycle.

### The rule that is easiest to break

**`core:navigation` must never reference a feature.** It does not know that
`HomeScreen` exists. Instead it takes the graph from the caller:

```kotlin
CompaneroScaffold {
    composable(Destination.Home.route) { HomeScreen() }
    composable(Destination.Profile.route) { ProfileScreen() }
}
```

Features depend on core. Core knows nothing about features. If
`core:navigation` ever imported a feature, the app module would hold a cycle
and the design would quietly invert.

## Error handling

One type, three layers of translation, and a feature never sees a Ktor
exception.

```
Ktor exception ─▶ AppError.Network / Http / Serialization / Unknown  (core:network)
                        │  userMessage: safe to display
                        │  technicalDetail: logged only, never shown
                        ▼
                  Outcome.Success / Failure                     (core:common)
                        ▼
              ContentState.Loading / Empty / Content / Failed   (core:ui)
                        ▼
                    ContentStateHost                            renders it
```

`AppError` is where the product decision lives. A 401 says the session
expired; a 403 says no permission; a 500 says try later. Those strings are
user-facing copy, and keeping them in one file means they can be reviewed for
accuracy by someone who is not an Android developer.

`technicalDetail` exists so debugging is possible without rendering a stack
trace to a student. The `ContentStateTest` suite asserts that no
`userMessage` contains a brace, which is a cheap guard against a raw
technical string slipping through.

## `ContentState` and the empty case

`Empty` is separate from `Content` on purpose. "You have no tasks today" and
"here are your tasks" are different screens, and collapsing them produces the
blank list with no explanation that users report as a broken app.

Emptiness is passed in as a predicate rather than assumed. A list with no items
is empty; a schedule with no items today might still want to show tomorrow.

## Navigation

`Destination` is a sealed class, not a string constant, so a typo in a route is
a compile error and a rename is one edit.

`CompaneroBottomBar` uses `saveState` / `restoreState` /
`popUpTo(startDestination)` / `launchSingleTop`, so switching tabs does not
grow the back stack without bound. Unbounded growth is the usual cause of a
bottom bar that stops behaving after a few taps.

## Configuration

`ApiEnvironment` validates its base URL at construction:

- must be absolute
- must end with `/`

`10.0.2.2` is the host machine as seen from the emulator. It lives in
`NetworkModule`, one obvious place, rather than in a resources file where a
wrong URL is invisible in review.

## Platform configuration

| Concern | Decision |
|---|---|
| Cleartext HTTP | Allowed only for `10.0.2.2`, `localhost`, `127.0.0.1`. Everything else requires HTTPS. |
| Cloud backup | Disabled entirely |
| Device transfer | Disabled entirely |
| Backup rules | `data_extraction_rules.xml` (API 31+) and `backup_rules.xml` (older) |
| Release build | Minified and resource-shrunk, with Proguard rules for serialization and Ktor |
| Dynamic colour | Opt-in, off by default |

Backup is off because attendance records and identifiers must not leave the
device through a provider we do not control. That is a privacy decision;
see [Location privacy](../privacy/LOCATION_PRIVACY.md).

Dynamic colour is off by default because attendance and schedule screens get
shared on school projectors, and Material You wallpaper extraction is not
reliably readable on unknown projector hardware.

## Testing

`core:testing` supplies `TestDispatcherProvider`, so coroutine tests are
deterministic without touching `Dispatchers.setMain` globally.

Every Android module sets:

```kotlin
tasks.withType<Test>().configureEach { useJUnitPlatform() }
```

This is not boilerplate. AGP defaults to the JUnit 4 runner, which **compiles
a JUnit 5 suite and then reports zero tests without failing**. Without this
line the Android test suite is a false green. See the
[bug register](../quality/BUG_REGISTER.md).

## Related

- [System architecture](SYSTEM_ARCHITECTURE.md)
- [ADR-001: module boundaries](ADR-001-MODULE-BOUNDARIES.md)
- [Design system](../ux/DESIGN_SYSTEM.md)
