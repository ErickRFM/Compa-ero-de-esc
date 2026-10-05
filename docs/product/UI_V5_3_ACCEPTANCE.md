# UI V5.3 + V6 — Acceptance Matrix

Integration baseline: `main@1b78b05` → `integration/v6-ui-v5-3-final`.
V5.3 reference: `feat/ui-v5-3-premium-polish@3728a5d`.

## Integration authority

- V6 remains authoritative for authentication, roles, multi-role switching, attendance lifecycle, offline-first capture, class channel, academic events and server verdicts.
- V5.3 is authoritative for visual hierarchy, semantic surfaces, compact density, responsive layout, accessibility and presentation.
- No V5.3 role-routing policy may bypass `RoleExperienceResolver`.
- Manual/OCR schedules never grant attendance authority.
- Signed QR and server verdict remain unchanged.

## Required visual states

| Surface | Compact 360–430 | Medium 600–839 | Expanded 840+ | Light/Dark | Large font |
|---|---|---|---|---|---|
| Login | required | required | required | required | required |
| Home student | required | required | required | required | required |
| Agenda day/week | required | required | required | required | required |
| Attendance student | required | required | required | required | required |
| Teacher home | required | required | required | required | required |
| Teacher attendance | required | required | required | required | required |
| Class channel | required | required | required | required | required |
| Profile | required | required | required | required | required |
| Appearance | required | required | required | required | required |

## Automated definition of done

- [x] V6 role resolver remains the only role-navigation authority.
- [x] Student and teacher destinations are isolated by resolver tests.
- [x] Teacher Home preserves direct access to Canal and Asistencia.
- [x] Top-level shell has no empty TopAppBar.
- [x] Compact semantic typography and shape hierarchy.
- [x] Semantic Surface API.
- [x] Neutral notices and dark surface ladder.
- [x] Grouped Profile and Appearance surfaces.
- [x] Multi-role experience switch remains available from Profile.
- [x] Stable subject color policy.
- [x] Continuous Agenda timeline.
- [x] Useful free-day Home state.
- [x] Attendance V6 requested-mode/offline/QR lifecycle preserved.
- [x] Attendance live hero treatment.
- [x] Automated critical contrast test.
- [x] Android CI includes design-system tests.
- [ ] Physical visual QA on target phone after installing the final main APK.
- [ ] Screenshot golden infrastructure (follow-up only if device screenshot tooling is adopted).

## Release gates

The reconciliation pull request must pass `ci-api` and `ci-android`.
Android CI compiles, runs unit tests, verifies that tests actually executed,
validates lint, packages debug and minified release APKs, performs the secret
scan, and stages traceable APK artifacts. Physical-device QA is the remaining
manual release gate after merge.
