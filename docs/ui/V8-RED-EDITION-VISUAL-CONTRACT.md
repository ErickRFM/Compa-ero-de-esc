# V8 Red Edition — student UI visual contract

Status: FOUNDATION IN PROGRESS. **No screen has been approved or merged.**

## Approved reference images (authoritative; no redesigns)
The five user-supplied images from 2026-10-07 are the only authorized visual references for V8. They must be compared with captures from a real Android emulator/phone before closing each screen:

| Screen | Original attachment filename | Implementation target |
| --- | --- | --- |
| Authentication | `1000038096.png` | `feature:auth` |
| Student Home | `1000038097.png` | `feature:home` |
| Weekly Schedule | `1000038098.png` | `feature:schedule` |
| Classroom Channel | `1000038100.png` | `feature:channel` / `feature:classroom` |
| Attendance QR | `1000038099.png` | `feature:attendance` |

**Do not substitute image search results or new generated mockups.** The attachment filenames identify user-owned references; they are not yet included in the Git repository. Asset import requires adding the approved assets to version control (licensed/owned assets only), not a screenshot behind dummy controls.

## Constraints
- Preserve the Kotlin + Compose + Ktor architecture, session security, roles, attendance rules, APIs, offline queue and persistence.
- Work within existing `core:designsystem` and `core:motion` modules. No duplicate theming or navigation stacks.
- References are visual specifications; names, dates, percentages, class and teacher data are dynamic, not hardcoded in production.
- Ensure usable keyboard/IME/insets, focus, error and loading states for Auth.
- Weekly grid: time-proportional class blocks, 0/N classes, Monday–Friday and other configured days, collision handling, drag preview, cancel/confirm semantics. No unintended overlapping.
- Attendance must distinguish school entry QR from classroom check-in and never imply Wi-Fi/GPS verification if evidence is unavailable.
- Student classroom channel: preset responses and allowed reactions only; no free-message composer.
- Avoid uncontrolled blur and animated glow that harm battery, accessibility or scrolling.
- Accessible touch targets, font scaling, dark contrast, reduced-motion support.

## Visual QA gate (per screen)
1. Build a runnable screen wired to the existing ViewModel/state, never static placeholder data for runtime.
2. Capture target Android devices (small width, normal width, large font; tablet where appropriate).
3. Side-by-side comparison against the precise corresponding approved image: layout hierarchy, hero art, gradient, typography, spacing, radii, buttons, nav and icons.
4. Correct discrepancies and run Compose/UI tests, feature unit tests and Android lint/build CI.
5. User signoff is required **before marking that screen visually accepted**. Merge requires green checks and explicit phase approval.

## Planned PR sequence
1. `feat/ui-v8-red-edition-foundation` — visual tokens, reusable components and contract.
2. `feat/ui-v8-auth`
3. `feat/ui-v8-student-home`
4. `feat/ui-v8-schedule`
5. `feat/ui-v8-classroom-channel`
6. `feat/ui-v8-attendance`
7. `test/ui-v8-visual-acceptance`

Only after V8 student closes: teacher -> tutor -> administrator. Each new role reuses the approved design primitives.
