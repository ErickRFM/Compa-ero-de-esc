# V13.2 — FCM delivery and Mongo outbox (security/operations)

## Boundary and production prerequisites

This release adds a **backend-only** Firebase Cloud Messaging HTTP v1 transport using Google service-account OAuth2 (RS256), a Mongo-backed delivery outbox, and a worker that processes queued destinations while the Ktor API is running.

Configure `FIREBASE_SERVICE_ACCOUNT_JSON_B64` as a **secret** containing standard service-account JSON with the correct `project_id`, `client_email`, RSA `private_key`, and Google's canonical `token_uri`. The service account needs the appropriate Firebase Messaging permission in the target Firebase project. Do not add JSON keys to source control, client APKs or CI logs.

- No credential: `UnavailablePushGateway`, no worker; academic data is still persisted and can be read in-app. Never simulate delivered notifications.
- Invalid credential JSON: startup configuration fails; no untrusted OAuth URL is contacted.
- Authenticated Mongo instance with valid credential: durable worker starts and stops with the Ktor application lifecycle.
- Current event trigger is intentionally **only a native, explicitly scoped STUDENT academic event**. GROUP/COURSE/INSTITUTION fanout requires its own authorized roster resolution, consent, cancellation and membership revalidation work. Do not infer recipients from display names or old imported enrollment data.

## Delivery properties

- A stable event/revision + owner + token SHA-256 is the idempotency key. Mongo `_id` unique constraint protects concurrent enqueue across replicas.
- A document's `SENDING` lease has a UUID and 90-second expiry. A competing worker can reclaim expired work but only the current lease holder can acknowledge.
- Each attempt has bounded backoff (first 30 seconds; capped at six hours); after seven attempts it is retained as `DEAD`. Completed/dead rows expire from Mongo after 30 days. Review `lastResult` and database metrics for health.
- FCM responses that explicitly contain `google.firebase.fcm.v1.FcmError: UNREGISTERED` deactivate the device registration; unrelated 404/401/403 errors **do not** invalidate user devices.
- Each FCM device registration is linked to the signed JWT's `session_id` at the API boundary. Legacy unbound registrations are *not deliverable* until the device registers again with a current session.
- Before every send, the backend rechecks both the current native student account and its non-revoked, unexpired refresh session. A locally logged-out session must also revoke its server session when online; offline logout cleanup requires Android SDK integration before production rollout.
- Before a push is sent, the current device registry is queried again. Stale work targeting a device no longer owned by the account is dropped.
- This is **at-least-once**, not exactly-once. A provider accepts a message before a process crash but Mongo is not yet acknowledged, so redelivery is possible. The app should de-duplicate message presentation with a stable event ID in the future.
- The currently supported push alert uses a **generic title/body** so lock-screen previews cannot reveal a student's academic/medical/tutoring details. Full content stays behind normal API permissions.

## Verification gate

- `FcmDeliveryTest`: OAuth assertion, caching, status taxonomy, invalid credentials, registration removal, idempotent queue, lease fencing/retry.
- `MongoPushOutboxIntegrationTest`: a real Mongo server, parallel worker contention, retry/lease fencing, permanent-result deduplication.
- `ci-api.yml` explicitly checks that the real Mongo suite ran (no skipped tests).
- `ci-android.yml`: existing Android compilation/lint/release and API31/API35 instrumentation remain required.
- Do not claim real-phone push delivery without configuring Firebase, obtaining an actual Android FCM registration token, and validating a notification across two devices with account swap, OS notification permission, network loss and logout.

## Still pending before full notifications rollout

- Android Firebase SDK/message receiver and registration/rotation on login/logout, Android 13+ notification permission, optional notification center persistence/UI.
- Scoped delivery to course/group/institution with membership revocation and opt-in/out.
- Worker telemetry, dead-letter administration, operational alerts and recovery playbook.
- Production Firebase credentials are not available in Git; no actual FCM push can be confirmed until explicitly provisioned.
