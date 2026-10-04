# Auth/session error resilience audit

Date: 2026-10-04

## Scope

Targeted audit of Android authentication failures that can surface as a generic
error even when the API itself is healthy:

- transport and HTTP classification;
- login 401 semantics;
- response/JWT parsing;
- secure token persistence;
- Android Keystore invalidation;
- stale encrypted session state;
- DataStore read failure;
- session restoration after app restart.

## Findings

### AUTH-ERR-001 — secure-storage failures were classified as Unknown

**Severity:** High  
**Status:** Fixed

A successful `POST /auth/login` could still render "Ocurrió un error
inesperado" when `SessionTokenStore.writeAccessToken` threw. The API and
credentials were healthy; the failure occurred after authentication while
persisting the JWT.

Fix:

- add `AppError.Storage`;
- use a specific safe user message;
- pin the behavior with an AuthRepository test.

### AUTH-ERR-002 — Android Keystore encryption had no recovery path

**Severity:** High  
**Status:** Fixed

The AES key can become unusable after device-security changes, restore, or
platform keystore invalidation. The previous implementation attempted
encryption once and surfaced the exception.

Fix:

- `TokenCipher.reset()` explicitly discards owned key material;
- AndroidKeyStore implementation deletes the session alias;
- session storage retries encryption exactly once with a newly generated key;
- repeated failure is propagated as a classified storage error.

### AUTH-ERR-003 — stale encrypted session could break restoration

**Severity:** High  
**Status:** Fixed

If persisted ciphertext/IV no longer matched the current key, decrypting it
could throw while the app was observing session state.

Fix:

- stale decrypt state is treated as signed out;
- direct reads best-effort clear corrupt encrypted state;
- observable reads emit `null` instead of cancelling the session flow.

### AUTH-ERR-004 — DataStore read failure could cancel session observation

**Severity:** Medium  
**Status:** Fixed

A DataStore read exception had no containment boundary at the session store.

Fix:

- the session token flow catches storage-read failures and emits signed-out
  state rather than leaving authentication initialization stuck.

### AUTH-ERR-005 — login 401 used expired-session copy

**Severity:** Medium  
**Status:** Fixed in the UPTLAX V5 auth pass

A 401 returned by `POST /auth/login` was presented as "Tu sesión expiró",
which is correct for protected-resource session invalidation but wrong for
invalid login credentials.

Fix:

- SessionViewModel maps login 401 to "Usuario o contraseña incorrectos.";
- generic 401 semantics remain available for protected API calls.

## Existing protections verified in code

- API base URL is absolute and validated at build/runtime boundaries.
- Network/timeout failures map to retryable `AppError.Network`.
- malformed/wrong-shape payloads map to `AppError.Serialization`.
- non-success HTTP statuses preserve their status code.
- JWT local inspection rejects malformed/expired tokens before persistence.
- institutional passwords are not persisted.
- the API remains the authority for JWT signature and authorization.

## New regression tests

- stale encrypted token becomes signed-out state rather than a crash;
- invalid keystore key causes one reset + retry;
- secure-session persistence failure becomes `AppError.Storage`;
- existing round-trip/clear session coverage remains intact.

## Acceptance gate

This area is considered closed only when:

1. Android security/auth unit tests pass;
2. debug APK builds;
3. physical-device login against Render succeeds;
4. process restart restores the valid session;
5. logout clears the session;
6. corrupt/invalid keystore state returns to login without app crash;
7. no generic "unexpected" message is used for known auth/storage failures.

