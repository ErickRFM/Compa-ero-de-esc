# Security model

Historical foundation status (superseded by the dated implementation cut below): authentication was not built.
This document describes what is enforced now and what must exist before any
real user data exists.

## Principles

1. **Institutional credentials never reach the device.** The API holds them.
2. **Fail closed.** A misconfigured environment stops the service rather than
   running in a degraded-but-wrong state.
3. **Never serve fabricated data to a real user.** Mocks are locked out of
   staging and production.
4. **Log the minimum.** A student's schedule and attendance are sensitive.
5. **No secret ever reaches git.** `.env` is ignored; `.env.example` is blank.

## Controls currently enforced

### Boot-time

| Control | Implementation |
|---|---|
| Configuration validated before serving | `SettingsLoader` throws `ConfigurationException` |
| Rejected values never logged | `ValidationResult` names the field, not the value |
| JWT required in production | `SettingsLoader` enforces a 32-character minimum |
| Mocks refused outside local/dev | `ProviderRegistry.requireEnvironmentSatisfied`, called from `Application.module` |

The mock lockout is the most important of these, and it is called from the
application module rather than only from a test. It was initially written and
never invoked; see the [bug register](../quality/BUG_REGISTER.md).

### Transport

| Control | Implementation |
|---|---|
| Cleartext restricted on Android | `network_security_config.xml` allows only `10.0.2.2`, `localhost`, `127.0.0.1` |
| HTTPS required elsewhere | `base-config cleartextTrafficPermitted="false"` |
| Base URL validated | `ApiEnvironment` requires an absolute URL |

A release build cannot silently send a student's data in the clear to a
mistyped host. Changing that requires editing an explicit, reviewable XML file.

### Data at rest on the device

| Control | Implementation |
|---|---|
| No cloud backup | Disabled in `data_extraction_rules.xml` and `backup_rules.xml` |
| No device-to-device transfer | Both excluded |
| Release minification | R8 with rules for serialization and Ktor |

Attendance records and identifiers must not leave the device through a provider
we do not control.

### Error handling

- All API errors use the shared `ApiError` envelope.
- `ApiErrorCode` is a closed enum; an unmapped exception becomes a generic
  `internal_error` with a `requestId`, never a stack trace.
- `/health` and `/version` never include a connection string. Covered by
  `HealthServiceTest` and `HealthRoutesTest`.
- Error bodies captured into `technicalDetail` are truncated to 512 characters.

### Supply chain

- Versions pinned in `gradle/libs.versions.toml`; no dynamic versions.
- A composed alias in the catalog (`androidx-compose-window-size`) was renamed
  because `class` is a reserved Gradle word in alias names.
- The Gradle wrapper is committed, so CI uses the same Gradle as every
  developer.

## Not yet implemented

Stated plainly, because a security document that implies more than exists is
worse than none.

| Area | Status |
|---|---|
| Student authentication | **Not built.** Ktor JWT plugin is in the catalog, unused |
| Token storage on device | **Not built.** Requires `core:security` |
| Token refresh and revocation | **Not built** |
| Rate limiting | **Not built** |
| Certificate pinning | Not built; revisit only if threat modelling justifies it |
| Institutional credential storage | Not built; will live server-side only |
| Encryption at rest | Relies on the Mongo provider's own controls |

## Requirements before any real user data

1. Authentication with a real session lifecycle and refresh.
2. `core:security` for credential storage on the device, using the Android
   Keystore rather than plain preferences.
3. Rate limiting on authentication endpoints.
4. A decision on whether institutional credentials are per-user or per-school,
   which changes the storage model entirely.
5. An audit log for access to attendance records.
6. A redaction pass over logs once real endpoints exist; body logging is off
   now, but a future logging change could reintroduce it.

## Secrets handling

- `.env` is git-ignored and must never be committed.
- `.env.example` documents every variable with values blank.
- `JWT_SECRET` is generated, never written down. The generator command is in a
  comment in `.env.example`.
- The only credentials currently in the repository are mock fixtures
  (`MockIdentityProvider.MOCK_PASSWORD`). They are a documented test constant
  for a fictional account and are refused outside local and development.

## Related

- [Threat model](THREAT_MODEL.md)
- [Location privacy](../privacy/LOCATION_PRIVACY.md)
- [API architecture](../architecture/API_ARCHITECTURE.md)
- [Integration architecture](../integrations/INTEGRATION_ARCHITECTURE.md)

## Implementation cut — 2026-10-09, V12 identity foundation

Current code includes native registration/login, optional institutional identity fallback, short-lived JWTs, hashed refresh families, rotation/replay revocation, `/auth/me` and live identity authority. The earlier foundation text is historical and must not be interpreted as the current authentication inventory.

V12 delivery 1 adds explicit account lifecycle, native authority revision binding, pending-session academic denial and atomic audited identity CAS. See [migration, evidence and limitations](V12_IDENTITY_LIFECYCLE_AUDIT_20261009.md). No public privileged-account mutation endpoint is enabled by this delivery. Production approval and provisioning still require current institutional/resource scope, individual operator MFA and the remaining V12 services and UI.
## Implementation cut — 2026-10-10, V12 universal registration

New public signup carries registration intent, no granted authority. Single-use email verification is revision-bound and atomic. Pending teachers/tutors cannot enter academic routes; workshop participants have only auth/workshop scope. Current Android restores this metadata from live native /auth/me and reuses existing encrypted credential rotation. See [registration migration, evidence and production requirements](V12_REGISTRATION_AUDIT_20261010.md). The historical “not built” table above is superseded by these implementation cuts.
