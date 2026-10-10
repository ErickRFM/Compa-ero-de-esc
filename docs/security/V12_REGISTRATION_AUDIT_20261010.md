# V12 registration delivery — 2026-10-10

Base: `2d69bd7e72de05b60d4370e63ef206ed90af4bbc`. Extends the existing native authentication and V8/V9 UI. This delivery does not claim the remaining approvals, provisioning or workshop domain complete.

## Implemented behavior

Four public profiles (student, teacher, tutor, participant) start with no roles and PENDING_VERIFICATION. Registration intent is separate from authority. Student verification grants STUDENT without claiming school identity; participant verification grants only WORKSHOP_PARTICIPANT. Verified teacher/tutor requests remain PENDING_APPROVAL without academic access. Public ADMIN/SUPER_ADMIN are rejected.

Email challenges use 256-bit random secrets, only SHA-256 persisted, one-hour expiry, current authRevision binding and atomic single-use confirmation. Verification rotates native credentials; previous JWT/refresh authority is invalid. Mongo confirmation updates verification, roles, lifecycle, revision and audit in one operation. Resend is storage-enforced at 60 seconds and 3 issues per rolling hour. Public signup additionally limits each address to 20 requests/5 minutes and each email/address to 8/5 minutes. Request bodies are bounded to 4 KiB before JSON parsing.

The configured mail adapter sends through Resend with bounded HTTP responses, no redirects and a stable idempotency key on retry. ACCEPTED means provider acceptance, never inbox delivery. Unavailable, failed and unknown delivery remain explicit UI states. No real provider credentials or external email were used for these tests.

Registration, email verification and pending approval share AuthV8Layout, themes, localization, the encrypted session store and live /auth/me restoration. Institution choices come from enabled platform directory records; missing/failed/empty directory is displayed honestly. Tutor group is a preference, never an assignment. Passwords, identity reference and verification code live in an activity-scoped memory-only ViewModel, survive activity recreation and are cleared on completion/back/logout/account change. They never enter Compose SavedState or disk.

## Migration and operational requirements

Existing active accounts remain compatible. New optional platform_accounts fields are registrationIntent, emailVerification and emailVerifiedAt; no public client can write authority. The existing normalized unique email index remains required. platform_institutions contains real enabled id/displayName records; teacher/tutor registration requires an existing record. Population and administrative management belong to the subsequent protected provisioning delivery, with no demo institution inserted here.

Deploy updated Android with these signup semantics. Configure VERIFICATION_EMAIL_API_KEY and VERIFICATION_EMAIL_SENDER together, with a verified sender and institutional DNS/mail ownership. Production delivery, SPF/DKIM/DMARC and operator identity remain external verification requirements. Multi-instance deployments require a shared edge throttle and explicitly trusted proxy configuration; current connection address is used without trusting arbitrary X-Forwarded-For.

## Verified evidence

Full local Gradle QA: API 240 tests (6 real-Mongo tests skipped locally because Mongo is absent), shared 23, Android JVM 196; zero failures. API30 instrumentation 29/29, zero failures/skips. Debug and Release assemblies and both lint tasks pass; each app lint report has 0 errors and 34 pre-existing warnings. Source UTF-8 audit passes. Secret scan passes.

The pushed backend head fee964d CI run 38024311993 executed all six real-Mongo lifecycle/verification tests with zero skips/failures. This is an intermediate backend head: complete-branch exact-head CI and API31/35 must separately pass before merge.

31 actual API30 PNGs were exported with infrastructure/scripts/export-v10-evidence.py after leaveApksInstalledAfterRun: 19 retained login references plus 12 registration captures at 360/390/430dp and 768dp tablet, English light contrast and 390dp large text/reduced motion. Visually inspected dark390 top and English light contrast form. Evidence remains ignored in build/v10-device-evidence/v10-evidence and is copied to the primary build/v12-qa directory before worktree archival.

Local intermediate APKs were built from this worktree while changes were pending; they are not the final V12 main release candidate. Final exact-main build and SHA-256 belong to the last sequential delivery. No human QA, production mailbox delivery, production MFA or physical-device validation is claimed.

## Fresh review and corrections

One fresh Astra review of immutable d0cc1ec found no critical and two important issues. Both received actual RED tests: populated tutor/verification drafts lost on actual activity recreation (2 API30 failures), and provider idempotency409/503→400 returned definite failure (2 transport failures). Corrections retain sensitive drafts in activity memory only and preserve provider uncertainty across retries. A participant protected-academic probe assertion covers the required negative isolation gate. The API31 smoke failure was traced to scrolling forward for a profile already above the viewport; its real interaction order and explicit test locale were corrected without changing production navigation. Official provider semantics: [Resend idempotency responses](https://resend.com/changelog/idempotency-keys).
