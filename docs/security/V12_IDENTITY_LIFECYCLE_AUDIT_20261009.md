# V12 identity lifecycle — delivery 1 (2026-10-09)

Baseline: `e51e516e971f055d89ea5f4c2853417e67431771`. This is the identity foundation, not completion of the V12 product or a production readiness claim.

## Changes

`platform_accounts` gains `accountStatus`, `authRevision`, `institutionId` and an embedded, secret-free identity audit. Account state is separate from granted roles. Access JWTs and persisted refresh snapshots carry the same authority revision and scope. Native identity revision changes make prior access and refresh unusable; suspending and later restoring the same roles cannot revive an older session. Re-authentication uses current authority. Institutional identities retain the existing live-provider checks.

Pending accounts may authenticate with no granted academic roles and inspect `/auth/me`; authenticated academic routes reject them centrally. A public registration selection never authorizes a privileged grant. This delivery does not expose an identity mutation HTTP endpoint. Subsequent administrative services must require current institution/resource scope and verified step-up before calling the persistence CAS primitive.

## Compatibility and migration

- Existing documents without lifecycle fields: explicit `active=true` maps to `ACTIVE`, otherwise `SUSPENDED`; missing revision maps to zero. This compatibility mapping does not claim email or institutional identity verification.
- Explicit unknown/null state and malformed/negative revision fail closed. The legacy active flag cannot override suspension or revocation.
- New writes persist the lifecycle fields. Authority mutations must go through the atomic CAS primitive, incrementing the revision with the audit event in the same document. Direct role/state changes outside this primitive are unsupported.
- `institutionId` is server-managed metadata at this stage. Its presence alone grants no academic group, no workshop event and no administrative scope. Existing academic institution enforcement is not certified by this delivery.
- Deploy all API replicas with the same lifecycle enforcement before enabling mutations; an older API ignores the new fields. Do not perform an unrestricted mixed-version rollout while changing privileges. Retire older server replicas and sessions as part of the later production release procedure.
- Retain existing password hashes, native IDs, email uniqueness indexes, refresh collections and academic resources. No destructive backfill is performed.
- Embedded audit is capped at 1,000 events per account. Further mutations fail closed; entries are never silently discarded. An explicit audited export/archival procedure is required before that exceptional quota can be freed. This initial delivery does not provide that archival procedure.

## Evidence and boundaries

Route regressions reproduce ignored suspension, restored-session resurrection, pending academic access and malformed authority metadata before implementation. Tests use real Ktor auth routes/services and the actual Mongo repository against a deterministic driver boundary; that fixture is explicitly not a running MongoDB.

The real-database concurrency suite is run separately in CI against an isolated Mongo service/database; local checks without that service must report the suite as skipped, never passed. Final test counts, CI heads and reviewer findings are recorded after execution.

Production email delivery, privileged owners/secrets, actual operator MFA enrollment, administrative approval screens and workshops remain subsequent V12 deliveries. No credentials are provisioned by this change.