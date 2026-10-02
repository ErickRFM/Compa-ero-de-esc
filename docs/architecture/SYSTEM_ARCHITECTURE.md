# System architecture

## Purpose

Compañero de Escuela gives a student one place to see today's classes,
attendance, timetable and institutional notices. This document describes how
the system is put together and, equally important, what it deliberately does
not contain yet.

## Context

```
┌──────────────────────┐
│  Android app         │
│  (Compose, Hilt)     │
└──────────┬───────────┘
           │ HTTPS + JSON
           │ never a direct connection
           ▼
┌──────────────────────┐
│  Our API             │
│  (Ktor 3)            │
└──────┬───────┬───────┘
       │       │
       │       └──────────────► MongoDB (attendance cache, sync state)
       │
       ▼
┌──────────────────────┐
│  Institutional      │
│  system              │
│  (SIS, LMS, library) │
└──────────────────────┘
```

The Android app **never** talks to an institutional system directly. Every
credential, every institutional quirk and every rate limit lives behind our
API. This is a security requirement, not a stylistic one; see
[ADR-001](ADR-001-MODULE-BOUNDARIES.md).

## Layers

### 1. Shared (`shared/*`)

Pure Kotlin. No Android SDK, no Ktor, no driver. Consumed by both the API and,
where useful, the Android app.

| Module | Responsibility |
|---|---|
| `shared:contracts` | Wire types: `ApiResponse`, `ApiError`, health payloads, `UserRole` |
| `shared:models` | Academic domain: `Student`, `Teacher`, `Course`, `Schedule`, ... |
| `shared:validation` | Config validation primitives used at boot |

Sharing these guarantees the client and the server cannot disagree about a
serialized shape. `ContractsTest` is the executable version of that promise.

### 2. Backend (`services/api`)

Ktor 3, JVM. Owns configuration, persistence, and every external integration.

Responsibilities, in order of importance:

1. **Refuse to start misconfigured.** `SettingsLoader` throws
   `ConfigurationException` naming the offending variable, and never echoes
   the rejected value.
2. **Refuse to serve fabricated data.** `ProviderRegistry` blocks mock
   providers outside local and development.
3. **Keep institutional quirks in adapters.** See
   [Integration architecture](../integrations/INTEGRATION_ARCHITECTURE.md).
4. **Answer operational questions.** `/health`, `/ready`, `/version`.

The API currently exposes **no product endpoints**. Authentication, QR
scanning, attendance submission and timetable retrieval are all future work.

### 3. Android (`apps/android/*`)

Single activity, Jetpack Compose, Hilt. `core:*` modules each own one
responsibility and depend only downward. See
[Android architecture](ANDROID_ARCHITECTURE.md).

### 4. External systems

Reached only through provider interfaces. Each capability (academic,
identity, learning, library, events, sync) is a **separate** interface, so a
future implementation can back some capabilities and not others. That is the
whole point: a school with no library system should not require a library
adapter.

## Data flow: one request, end to end

```
External SIS response
  → ExternalStudent (DTO, external vocabulary)
  → AcademicMappers.toStudent  (the only place that knows the quirk)
  → Student (our domain)
  → ApiResponse (our wire contract)
  → Android Outcome
  → ContentState
  → Compose UI
```

Each arrow crosses exactly one boundary. A reviewer should be able to point at
the file where any of these crossings happens.

## Failure behaviour

| Failure | Behaviour |
|---|---|
| Mongo not configured | Service starts. `/health` 200 degraded, `/ready` 503 |
| Mongo unreachable | `/health` degraded, `/ready` 503 |
| Upstream returns a malformed field | `IntegrationException(MALFORMED_RESPONSE)`, not silent drop |
| Upstream returns unknown student | `NOT_FOUND`, distinct from an auth failure |
| Client cannot reach the API | `Outcome.Failure(AppError.Network)`, user sees a retry |
| API returns 5xx | `AppError.Http` marked retryable |
| API returns 401 | `AppError.Http` marked **not** retryable |

## What is explicitly out of scope

Not implemented, and not stubbed:

- Student authentication and session management (the Ktor JWT plugin is in
  the catalog but unused)
- QR generation and scanning
- GPS, BLE and Wi-Fi detection
- Real institutional API clients
- FCM / push notifications
- Offline sync and conflict resolution
- Any user-facing data beyond two placeholder screens

The reason is recorded in [Roadmap](../product/ROADMAP.md).

## Related

- [Android architecture](ANDROID_ARCHITECTURE.md)
- [API architecture](API_ARCHITECTURE.md)
- [ADR-001: module boundaries](ADR-001-MODULE-BOUNDARIES.md)
- [Security model](../security/SECURITY_MODEL.md)
