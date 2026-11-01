---
name: companero-siii-sync
description: Audit, implement and certify institutional academic synchronization between SIII Next.js, Compañero Ktor/Mongo and Android Kotlin. Use for missing official schedules, unenrolled accounts, HMAC/outbox, stale course data, grades, identities, notifications, or attendance E2E.
---

# Compañero ↔ SIII: safe academic synchronization

## Mandatory order
1. **Read the current repositories, not old roadmaps.** SIII is `ErickRFM/Siii`; receiver is `ErickRFM/Compa-ero-de-esc`. Capture exact `main` SHAs and deployment SHAs before changing anything.
2. Trace this data path separately for each student/teacher and institution:
   - SIII authorized student/teacher, active institution, enrollment, academic section and published information.
   - SIII transactional outbox / signed HMAC event / delivery and replay protection.
   - Ktor institutional projection worker, identity links and durable `MongoSiiiSectionSnapshotRepository`; do not acknowledge dropped or unsupported events.
   - `SiiiAcademicService` and `SiiiSectionReader` check current source authority, link by native user ID and institutional role, section revision and membership.
   - `AcademicService.loadFor` → `/academic/load` → Android `AcademicRepository` → `HomeViewModel` → Hoy/Horario.
3. Do not infer source data from a synthetic test. A valid SIII↔Ktor contract does **not** mean group 9A or any real enrollment has been provisioned.
4. Diagnose at the failing boundary using redacted request identifiers, status categories, tenant and version; never print bearer/HMAC/FCM tokens, educational records or personal details. Use existing authorized tools. **Never create production enrollments or grant roles merely to make the UI show data.**
5. Preserve provenance: `ScheduleSource.INSTITUTIONAL` records may authorize institutional actions; imported PDF/manual agenda is `PERSONAL`/`MANUAL` and must not grant attendance or academic authority. No silent fallback from 401/403/revocation into stale official cache.
6. Reconcile identity, role, institution, enrollment and snapshot version; invalidate on revocation, 401/403 and account switch; don't use a mock provider in production. Fail closed on missing links or incomplete snapshots.
7. Tests: `./gradlew :services:api:test --tests '*SiiiAcademicRoutesTest' --tests '*SiiiFullChainLiveContractTest'`, `./gradlew :apps:android:core:academic:test`, `./gradlew :apps:android:feature:home:test`, then API and both Android CI suites. Verify exact tasks supported by the repository before running.
8. Pilot gate with **authorized QA accounts only**: same 9A group/period and teacher assignment in SIII; check student/teacher Hoy and Horario, updates, cancellation, revocation, and offline behavior. For attendance, open teacher session → FCM notification → deliberate student confirmation → teacher disposition → server history → SIII read. Test foreground, background, killed app and denied notifications on physical Android devices.
9. Publish only through reviewed PR, green CI and traceable release. If live infrastructure, authority or credentials are unavailable, document blocker and keep the status **not certified**.

## Working files
- `services/api/src/main/kotlin/org/companerodeescuela/api/integrations/academic/siii/`
- `services/api/src/main/kotlin/org/companerodeescuela/api/academic/AcademicService.kt`
- `services/api/src/main/kotlin/org/companerodeescuela/api/academic/AcademicRoutes.kt`
- `apps/android/core/academic/AcademicRepository.kt` (locate within `src/main`)
- `apps/android/feature/home/.../HomeViewModel.kt`
- `docs/audits/V21_INTEGRAL_DELIVERY_GATES_20261029.md`

## Evidence to report
Explicitly separate **implemented**, **CI verified**, **deployed**, and **physically tested**; include the SHAs and precise negative cases.
