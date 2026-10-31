---
name: companero-android-qa
description: Systematically debug and certify Compañero's Kotlin Jetpack Compose UI, role login, accessibility, animations, two APK distributions, attendance, and flaky Android emulator tests. Use for UI regressions, pager issues, missing test tags, broken responsive layouts or Android CI failures.
---

# Compañero Android: investigate → fix → certify

## Root-cause protocol
1. Capture the exact Git SHA and failing CI jobs, API level, flavor, test name and assertion. Read failure artifacts/screenshots when available. Do not assume emulator flakes or dismiss an actual UI defect.
2. Trace event → state → composable: interaction, coroutine / pager animation, active `AppExperience`, `AppDistribution`, test semantics and resulting screen. Compare against a working API level/flavor.
3. Form a falsifiable hypothesis, reproduce **RED**, implement the smallest fix, rerun **GREEN**. Avoid adding long sleeps, disabling checks, increasing timeouts blindly or removing accessibility labels. Use state-based waiting if a transition truly is asynchronous.
4. Keep six access choices in LEGACY and the strict Academic/Management subsets defined by `AppDistribution.allows`; **selecting an experience never grants a server role**.
5. UI composition belongs in `core:designsystem` and existing V8 Compose components. Reuse `V8FrostedGlassPanel`, typography, buttons, navigation, error/empty/loading states. Avoid inventing a second theme, duplicated cards or unnecessary glass borders.
6. Motion: prefer direct, predictable chip selection; arrow/swipe transitions may animate when allowed. Respect reduced motion and keep actions reachable on small screens. Apply design review practices (performance, restraint, overflow and worst-case data) suited to *native Compose*, not web or Expo APIs.
7. Verify both distributions with actual instrumented tests on API 31 and 35; cover 360/390/430 dp, 200% font, TalkBack semantics, keyboard, rotation, dark/light and notification permissions.
8. Certify one-tap attendance without conflating FCM receipt and confirmed presence. Test revoked tokens, duplicate deep links, offline state and background/killed app before claiming reliability.
9. Before merge: compile, unit tests, ktlint, instrumented tests and both APK packages on the final SHA. Never declare hardware-tested without a real device run.

## Relevant paths
- `apps/android/feature/auth/src/main/kotlin/org/companerodeescuela/feature/auth/LoginAccessCarousel.kt`
- `apps/android/app/src/androidTest/kotlin/org/companerodeescuela/SmartRoleLoginUiTest.kt`
- `apps/android/core/designsystem/`
- `apps/android/core/motion/`
- `apps/android/app/src/main/kotlin/org/companerodeescuela/notifications/`
- `docs/audits/V21_INTEGRAL_DELIVERY_GATES_20261029.md`

## Reporting
State exactly which job and API/flavor passed or failed, link the run and attach any visual evidence. If offline, credentials or hardware are unavailable, mark those checks as pending.
