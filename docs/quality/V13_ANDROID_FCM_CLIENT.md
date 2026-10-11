# V13 — Optional native Android Firebase Messaging client

## Public project configuration

Set **all four** environment variables before building a configured APK:
- `COMPANERO_FIREBASE_APP_ID`: Android Firebase App ID from the project console, in the form `1:<sender-number>:android:<hex>`
- `COMPANERO_FIREBASE_PROJECT_ID`: Firebase project ID (same project as backend)
- `COMPANERO_FIREBASE_SENDER_ID`: numeric Cloud Messaging sender ID
- `COMPANERO_FIREBASE_API_KEY`: **public Android Firebase API key**, never a Firebase service account or private key

When missing, Firebase is disabled and the normal debug/Release build continues functioning. A partially populated configuration fails the Gradle build. The backend separately requires its service-account secret `FIREBASE_SERVICE_ACCOUNT_JSON_B64`, never packaged in Android.

## Operational flow

1. App startup creates the default FirebaseApp **only if** public project options were all configured.
2. Once a session is available, WorkManager schedules a network-constrained device-registration job; **it never receives tokens or JWTs through WorkData**.
3. On Android 13+, the worker skips Firebase token retrieval/registration without explicit `POST_NOTIFICATIONS` permission. The existing V8 Profile has a dedicated **Notificaciones** setting that requests this permission. On older Android versions, registration requires OS notifications to be enabled.
4. The worker obtains the current FCM token from the SDK and verifies `/auth/me` using the app's existing session/refresh coordinator. Only active, email-verified accounts register, using `/devices/register`, bearer JWT and a random installation ID stored in app-private preferences.
5. FCM's `onNewToken` schedules the same uniquely named WorkManager task. Account changes also schedule registration; backend enforces unique token ownership and verified `session_id` binding.
6. Foreground notifications deliberately display only generic text. Their PendingIntent opens the authenticated app rather than exposing a class, grade or student identifier.

## Security / limits

- The server rechecks account and refresh-session revocation before sending. Android cancels registration work on logout, but offline logout cannot revoke a server session until connectivity returns; do not claim immediate remote revocation in that situation.
- No Firebase token is stored in unencrypted WorkManager job input. The SDK is the source of the token.
- Real background and foreground notifications require **a valid Firebase project, matching app sender/project IDs, running Ktor backend and real devices**.
- A push is a *hint*, not authority: all academic details must be refetched through the backend's protected endpoints.
- Permission revocation, changing accounts, delayed FCM message delivery and background restrictions require device QA before production approval.
