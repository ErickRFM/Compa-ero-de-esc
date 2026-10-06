# Academic Agenda V7 — Preflight Audit

Base: `main@3a807cf64f1be725aa8cf69e6a8e76e59e619d9d`

## Scope

This audit records the current academic-data seams before Agenda V7 work. The goal is to preserve the existing V6 role/event architecture while isolating development fixtures from clean QA identities.

## Existing architecture found

- Institutional academic seam already exists as `AcademicProvider` under `services/api/.../integrations/academic`.
- `ProviderRegistry` already prevents mock providers from running in staging/production. Agenda V7 must reuse this safety boundary rather than introduce a second provider hierarchy.
- Development fixture data lives in `MockAcademicProvider` / `MockFixtures`.
- Development authentication lives in `MockIdentityProvider`.
- Academic UI contracts currently use `ScheduleSource.INSTITUTIONAL | MANUAL | OCR_IMPORT`.
- `AcademicEvent` is already the canonical event model for cancellations, reschedules, room changes and notices.
- Android already has a schedule OCR/import flow with preview-before-persist semantics.

## Fixture classification

### TEST_FIXTURE
Test-only payloads under `src/test`. Safe when they never enter runtime wiring.

### PREVIEW_DATA
Compose previews and visual-only sample state. Safe when unreachable from runtime repositories/providers.

### DEVELOPMENT_SEED
The accounts and academic fixtures exposed by `MockIdentityProvider` and `MockAcademicProvider`. Allowed only behind the existing mock-provider/local-development boundary.

### RUNTIME_DEMO
The current risk is not a separate dataset: `MockAcademicProvider.getAcademicLoad()` and `getSchedule()` return the Ana/Elena fixture for any recognized/queried identity path. A clean QA identity must never inherit `MockFixtures.ENROLLMENTS` or `MockFixtures.SCHEDULE`.

## Confirmed source of the leaked demo schedule

`MockFixtures` contains:
- teacher: Mtra. Elena Ríos Salgado;
- subjects: Álgebra Lineal and Historia Contemporánea;
- schedule slots on Monday, Tuesday and Thursday.

The provider currently returns those shared lists without owner scoping. Agenda V7 must scope fixture data explicitly.

## Implementation decisions

1. Keep the existing `AcademicProvider`; do not create a duplicate `SchoolAcademicProvider`.
2. Preserve the existing environment guard: mock providers remain unavailable in staging/production.
3. Keep Ana/Elena as explicit development demo identities for legacy tests and demonstrations.
4. Add explicit `qa.*` identities that return empty academic state.
5. Add canonical provenance/schedule contracts in separate files instead of overloading `AcademicEventContracts.kt`.
6. Reuse `AcademicEvent` for teacher/admin class lifecycle changes.
7. Keep import flow as parse -> normalize -> preview -> confirm -> persist.
8. Reconciliation rules belong in shared/backend domain code; Android must consume the result rather than invent precedence.

## QA identity policy

- `qa.alumno` -> STUDENT
- `qa.docente` -> TEACHER
- `qa.supervisor` -> existing COORDINATOR experience (no new RBAC role is introduced)
- `qa.admin` -> ADMIN
- password for all: `qa-development-only`

These identities are implemented only by the mock identity provider, which is already rejected outside local development by `ProviderRegistry`.

## Acceptance gate for this wave

- Demo user `ana.lopez` keeps the existing fixture.
- QA student resolves successfully but receives empty enrollments/schedule.
- QA teacher schedule is empty.
- Unknown academic identities still return NOT_FOUND where required.
- Existing demo fixtures remain available to tests that depend on them.
