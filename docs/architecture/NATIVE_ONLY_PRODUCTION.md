# Native-only production composition

Status: focused verification green; full verification and remote CI pending. No production settings were read or changed.

The API entry point calls Application.module without a registry override. The default now selects existing fixtures only for LOCAL/DEVELOPMENT and explicit unconfigured institutional providers for STAGING/PRODUCTION. Native Mongo accounts, sessions, academic projections, classroom and attendance graphs retain their existing composition. ProviderRegistry.configured remains the seam for reviewed real integrations; the environment guard still rejects explicitly supplied mocks before routing.

Unconfigured academic, learning, library, event, role-refresh and sync operations throw sanitized IntegrationException.UNAVAILABLE. Institutional authentication/currentState return null, granting no identity or current authority. These adapters do not return empty successful institutional lists or successful sync outcomes, create accounts, infer native source, or invoke an external service. A native account with a bad password still denies without institutional fallback.

## Verification contract

- Existing four application boot tests retain rejection of explicit mocks in STAGING/PRODUCTION and acceptance of default development fixtures.
- NativeOnlyProductionTest uses the real default production module and disposable local Mongo. It verifies health/readiness, native login/load/schedule, unknown/wrong-password denial, source-collision denial for INSTITUTIONAL/LEGACY, refresh, immediate logout revocation of access/refresh, and live Mongo account suspension denial.
- NativeOnlyProvidersTest exercises environment defaults and all fourteen institutional methods: twelve explicit unavailable failures and two identity denials.
- RED observed before product edits: four existing boot tests passed; new production test failed at ProviderRegistry mock guard. Scoped GREEN: 7/7 (four boot guards, two provider tests, one real Mongo production/auth/source test), zero failures/errors/skips; scoped formatting/check passed. Full verification is pending.

## Configuration and promotion

This change introduces no setting or secret name. Production already requires APP_ENV=production, API_PORT, MONGODB_URI, MONGODB_DATABASE, JWT_SECRET (at least32 characters) and MFA_ENCRYPTION_KEY (canonical unpadded base64url of32 bytes). APP_VERSION is optional release metadata. The deployed presence/validity of those values is unknown from this source-only task: it is not established that changing APP_ENV alone will suffice.

Optional existing feature configuration remains unchanged: paired VERIFICATION_EMAIL_API_KEY/VERIFICATION_EMAIL_SENDER for real onboarding delivery, distinct INSTITUTION_INTEGRATION_ENCRYPTION_KEY for the signed inbox/worker, ATTENDANCE_QR_SECRET for attendance signing, and FIREBASE_SERVICE_ACCOUNT_JSON_B64 for push. Native-only boot does not require provisioning an institutional provider credential. Do not enable Siii source delivery until its sender/reconciliation/revocation flow is proved.

Health/readiness reports process and Mongo state; it does not certify disabled integrations or complete mobile connectivity. APP_ENV promotion is a separate operational step after CI/review and a presence-only configuration check, without dumping values. Receiver PR198 is the source dependency; reconcile its merged main before publishing this branch. No Android contracts, public endpoints, schema, roles, permissions, or source emitter change in this block.
