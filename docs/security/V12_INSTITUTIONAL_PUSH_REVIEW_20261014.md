# V12 institutional push ingress review — 2026-10-14

## Review scope

Manual internal review of the signed Ktor ingress, credential storage, Mongo replay protection, event receipts, request limits, and the current application wiring. This is not an external security review. It does not approve the unfinished projection into academic schedules or Android clients.

## Controls confirmed in code

- The HMAC covers the HTTP method, fixed route, timestamp, nonce, and SHA-256 of the raw request body. Signature comparison uses `MessageDigest.isEqual`; timestamps have a bounded skew and nonces are unique per credential with TTL cleanup.
- Tenant identity and write scopes come from the active integration credential. Request payloads cannot select another tenant, and Mongo receipt keys include tenant identity.
- Credential material is encrypted with AES-256-GCM using `INSTITUTION_INTEGRATION_ENCRYPTION_KEY`, separate from MFA secrets. There is no public credential-provisioning route; deployment tooling must provision and revoke keys out of band.
- The receiver rejects unsupported envelope fields and versions, malformed UTF-8, oversized bodies, invalid content types, stale source versions, and conflicting replays. Duplicate upsert races are checked by Mongo error code rather than by exception subtype.
- Mongo is required for authenticated deployments; missing Mongo or encryption configuration leaves the endpoint unavailable. A Mongo-backed fixed-window limiter caps each resolved client address at 120 requests per minute before reading the body. Forwarded client addresses are accepted only through configured trusted proxies.

## Findings and limits

- No cross-tenant write path was found in the receiver: the only tenant value used for persistence is resolved from the verified credential. The envelope currently stores a generic JSON payload; entity-specific reference validation and projection into existing academic models remain incomplete and must be completed before this API is treated as an end-to-end academic integration.
- TLS termination, secret delivery, credential issuance/rotation, and key rotation are deployment responsibilities and are not implemented by this route. Production rollout must provide HTTPS, restrict access to the encryption key, and rotate credentials by overlapping active key IDs before revocation.
- The current rate limit is per client address, not per institution. Production proxy trust must be configured correctly; otherwise requests use the direct peer address. This control complements, but does not replace, edge-level abuse protection.
- The initial Mongo CI run exposed an invalid explicit unique `_id` index for Mongo 7 (error 197). The redundant index creation was removed; Mongo’s built-in `_id` index remains unique. The corrected SHA is awaiting its full CI result.

## Status

Manual review found no additional blocker in the ingress controls above. The projection and per-entity validation gaps are implementation blockers for completing V12 and remain open. No automated or external reviewer approval is claimed.
