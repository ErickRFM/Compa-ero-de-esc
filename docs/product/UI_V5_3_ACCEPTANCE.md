# UI V5.3 — Acceptance Matrix

Baseline: `main@c6d4310` → branch `feat/ui-v5-3-premium-polish`.

## Product rules

- UPTlax remains the source of institutional identity and authority.
- Manual/OCR schedules never grant attendance authority.
- Signed QR and server verdict remain unchanged.
- UI changes must preserve dark/light mode, reduced motion, high contrast and offline states.

## Required visual states

| Surface | Compact 360–430 | Medium 600–839 | Expanded 840+ | Light/Dark | Large font |
|---|---|---|---|---|---|
| Login | required | required | required | required | required |
| Home student | required | required | required | required | required |
| Agenda day/week | required | required | required | required | required |
| Attendance student | required | required | required | required | required |
| Teacher home | required | required | required | required | required |
| Teacher attendance | required | required | required | required | required |
| Profile | required | required | required | required | required |
| Appearance | required | required | required | required | required |

## V5.3 definition of done

- [x] Top-level shell has no empty TopAppBar.
- [x] Compact semantic typography and shape hierarchy.
- [x] Semantic Surface API.
- [x] Neutral notices and dark surface ladder.
- [x] Grouped Profile and Appearance surfaces.
- [x] Stable subject color policy.
- [x] Continuous Agenda timeline.
- [x] Useful free-day Home state.
- [x] Attendance live hero treatment.
- [x] Teacher Home workspace and role-aware navigation.
- [x] Automated critical contrast test.
- [ ] Physical visual QA on target phone after APK install.
- [ ] Screenshot golden infrastructure (follow-up only if device screenshot tooling is adopted).

## Release gates

The pull request must pass `ci-api` and `ci-android`. Android CI compiles,
runs unit tests, validates lint, packages debug and minified release APKs and
runs the secret scan. Physical QA is the only remaining manual gate.
