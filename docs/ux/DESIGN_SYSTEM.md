# Design system

Status: V4 expressive evolution in progress. The foundation is already used by
Login, Hoy and Agenda; V4 keeps the existing accessibility/offline principles
while adding a stronger brand palette, expressive shape scale, semantic spacing
and shared gradient tokens. Motion is defined separately in MOTION_MATRIX_V4.md.

## Principles

**Designed for the real device.** A low-end phone, a small screen, bright
sunlight, and a student who is tired between classes. Every decision is made
against those conditions.

**Legible before attractive.** An unreadable schedule is a failed product.
Contrast is checked before a colour is used.

**One step larger than Material.** The app is read at arm's length, in a
hallway. Material's default type scale is designed for a desk.

**Projector safe.** Screens that get shown to a class cannot depend on
wallpaper colours, because projector hardware varies wildly.

## Colour

Defined in `apps/android/core/designsystem/.../theme/Color.kt` and applied in
`Theme.kt`.

| Role | Light | Dark | Used for |
|---|---|---|---|
| Primary | Electric violet | Violet light | Navigation, hero actions |
| Secondary | Cyan | Cyan light | Active/supporting surfaces |
| Tertiary | Amber | Amber light | Warnings, late arrival |
| Error | Red | Red light | Destructive, failures |

Every `on*` pairing is chosen for contrast against its container. The palette
is defined once and consumed through `MaterialTheme.colorScheme`; a screen
never hard-codes a colour.

**Dynamic colour is opt-in, and off by default.** Material You extracts
colours from the user's wallpaper, which on an unknown device can produce a
primary colour with poor contrast against white text. Attendance and schedule
screens get shared on school projectors. Accessibility wins over
personalisation here.

## Typography

`CompanionTypography` in `theme/Typography.kt`.

| Role | Size | Line height | Use |
|---|---|---|---|
| `headlineMedium` | 28sp | 36sp | Screen title |
| `titleLarge` | 22sp | 28sp | Section header |
| `bodyLarge` | 17sp | 26sp | Primary content |
| `bodyMedium` | 15sp | 22sp | Secondary content |
| `labelLarge` | 15sp | 20sp | Buttons, tabs |

All weights are `Normal` to `SemiBold`. No `Bold` for body text: at these
sizes on a low-density screen, heavy weights look blurry.

## Shape

| Role | Radius | Use |
|---|---|---|
| `small` | 10dp | Compact controls, list items |
| `medium` | 18dp | Standard cards, dialogs |
| `large` | 24dp | Prominent cards, sheets |
| `extraLarge` | 32dp | Hero surfaces, floating navigation |

Slightly rounder than Material's default. The tone is a study companion, not
a bank.

## Spacing

A 4dp base grid: 4, 8, 12, 16, 24, 32. Screen padding is 16dp minimum and
32dp on a centred empty state. Touch targets are at least 48dp, which matters
more than usual for a student tapping quickly between classes.

## Shared components

Shared components and tokens are promoted only when more than one surface needs
them. V4 adds public spacing and gradient tokens so feature modules do not
invent raw visual values.

| Component | Purpose |
|---|---|
| `ContentStateHost` | Renders loading, empty and error for any data-backed screen |

`ContentState` separates `Empty` from `Content`. "No tasks today" and "here
are your tasks" are different screens, and merging them produces the blank
list that users report as a broken app.

## Error copy

All user-facing error text lives in `AppError` in `core:common`, not in
screens. This is a product decision in a single reviewable place.

| Error | Message |
|---|---|
| Network | Could not connect. Check your connection and try again. |
| 401 | Your session expired. Sign in again. |
| 403 | You do not have permission to see this. |
| 404 | We could not find what you were looking for. |
| 5xx | The service is not available right now. |
| Unknown | Something unexpected happened. |

`technicalDetail` is logged and never rendered. `ContentStateTest` asserts no
user message contains a brace, as a cheap guard against a technical string
slipping into user-facing copy.

## Not decided yet

Still gated by implementation evidence: final icon family, Rive/Lottie asset
scope, tablet composition, localisation and motion tuning on physical low-end
devices. These are intentionally not hard-coded into feature modules.
