# Auth, connectivity and Android CI audit — 2026-10-11

## Scope and status

Source review of `main@b09ef768cef87c85932b08cb575dcb252912ee77`:
Android Compose login and navigation, Ktor client, error classification,
session refresh, Android Keystore, Ktor authentication and identity provider
boundaries, Render URL build configuration and GitHub Actions.

**Runtime acceptance is not complete**: Render /health, /ready, /version,
MongoDB, the exact installed APK and real-device login were not accessible
from this execution environment. No production configuration, credentials,
accounts or database records were changed.

## Confirmed findings addressed in this branch

1. **CI attendance signature drift:** Two test doubles still implemented
   `nextReady(nowEpochSeconds)` instead of
   `nextReady(nowEpochSeconds, ownerId)`. This caused both Android JVM and
   instrumented test compilation to fail on main.
2. **Misleading login error classification:** HTTP 400, 404, 408, 409 and
   422 previously appeared as one generic error. Login now distinguishes
   invalid request/contract, missing sign-in endpoint, timeout and conflict
   from invalid credentials (401), rate limits (429) and server failures (5xx).
3. **Lost server correlation:** The server sends `X-Request-Id`, but the
   Android HTTP layer discarded it on failure. The client now retains only
   ASCII alphanumeric/hyphen IDs bounded to 80 characters and shows them as
   a safe support reference. HTTP response bodies, passwords, tokens and
   sensitive headers are not displayed.
4. **Teacher session recovery (CI regression):** `refreshTeacher()` previously
   returned before its offline fallback could run. It now restores only a
   recently cached, owner-and-class-scoped teacher pass when all failed
   dependencies are transient; a 401/403/404 never grants offline authority.
5. **Roster isolation and bounded work:** a malformed or temporarily unavailable
   roster response previously cleared the teacher's valid attendance session
   and signed QR. Ancillary roster failures no longer revoke authorization,
   and polling stops rather than retrying forever while offline.
6. **Test coverage:** HTTP request ID sanitization, login error mapping, and
   class-isolated offline attendance flows have regression tests.

## Still requiring live evidence

- **APK target:** Debug defaults to
  `https://compa-ero-de-esc.onrender.com/`, unless overridden at build
  time by `COMPANERO_API_BASE_URL`. Inspect the generated BuildConfig and
  the exact APK installed on the physical phone. Release intentionally
  defaults to `https://invalid.invalid/` without separate configuration.
- **Server availability:** `/health` is liveness; `/ready` verifies Mongo.
  Both may disagree, so check both HTTP statuses and the reported dependency
  status. Correlate failing requests using `X-Request-Id` in server logs.
- **Credentials:** The project historically exercised demo identities
  against its Render development configuration. They remain intact, but
  their validity on the currently deployed instance cannot be inferred
  from their presence in MockIdentityProvider. Do not reset user accounts
  or bypass `ProviderRegistry` environment restrictions.
- **Authentication lifecycle:** Use an authorized QA account to verify
  POST `/auth/login` -> 200, secure JWT storage and GET `/auth/me` -> 200
  with the same identity. Verify wrong credentials -> 401, logout, cold
  restart, token expiry/revocation and offline recovery.

## Merge / release gates

- CI Android unit tests, instrumentation compilation/execution, lint and
  Debug/Release APK packaging must pass at the final PR SHA.
- API tests must pass with the shared contracts in the same merge.
- Do not claim successful deployment or physical login from compilation
  alone. Test on the user's exact device and captured APK before release.
- Preserve V8 UI, existing account/role policy, secure token handling and
  access restrictions. No mock provider should ever be activated by
  weakening security in staging/production.
