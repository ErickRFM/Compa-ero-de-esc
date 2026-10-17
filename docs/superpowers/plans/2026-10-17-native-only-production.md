# Native-only production composition Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Boot Ktor safely in production using native Mongo identities and features while unconfigured institutional providers explicitly deny or report unavailable.

**Architecture:** Keep current provider interfaces and configured registry seam. Default LOCAL/DEVELOPMENT to existing mocks; default STAGING/PRODUCTION to non-mock unavailable institutional adapters. Preserve source-aware native session authority and all native feature graphs.

**Tech Stack:** Kotlin/JDK17, Ktor, MongoDB Kotlin coroutine driver, JUnit5, Gradle.

**Spec:** Approved bounded proposal in original Siii workspace .p0-local/companion-native-only-production-plan.md, receiver dependency95c5402b (PR198).

## Global Constraints

- No production environment changes or secret reads; no new secret names or mode variables.
- No Android, public route, role, permission, Siii emitter or schema changes.
- Retain explicit mock rejection in STAGING/PRODUCTION before routing.
- Unavailable data/sync methods must fail explicitly, never return fabricated or empty successful institutional data.
- Institutional and legacy sessions may not gain native authority through a matching account ID.

## Review Focus

- Native credentials must authenticate against Mongo; wrong password and unknown user deny without fallback authority.
- Existing institutional/legacy sessions fail closed; live native revocation remains immediate.
- Default production boot requires real disposable Mongo in tests; explicitly injected mocks still fail.
- Disabled provider methods all preserve their interfaces and sanitized failure category.
- Current production-required settings remain required; current deployment presence is unknown.

### Task1: Native-only registry and real production composition

**Files:** Modify ProviderRegistry.kt, application/ApplicationModule.kt (default only), application/ApplicationModuleBootTest.kt. Create integrations/UnavailableInstitutionalProviders.kt, integrations/NativeOnlyProvidersTest.kt and application/NativeOnlyProductionTest.kt in existing API main/test package roots.

- [x] Run existing boot guards plus new production/Mongo/native auth regression and observe RED at default mock guard.
- [x] Test registry environment defaults and every disabled provider operation; identity authenticate/currentState deny, refreshRoles fails.
- [x] Implement nativeOnly/defaultsFor registry factories and explicit unavailable implementations; update module default while retaining configured seam and guard.
- [x] Run focused tests with real disposable Mongo, including native login/load/refresh/logout/revocation and colliding institutional/legacy source sessions.
- [ ] Run scoped formatting, full API/shared tests, ktlintCheck and installDist; record actual evidence.
- [ ] Obtain independent review; reconcile main after PR198 merge, publish separate PR, require API/Android CI before merge.

Production promotion is a later operational action. Existing names: APP_ENV, API_PORT, MONGODB_URI, MONGODB_DATABASE, JWT_SECRET and MFA_ENCRYPTION_KEY. No claim is made that their deployed values are present or valid.
