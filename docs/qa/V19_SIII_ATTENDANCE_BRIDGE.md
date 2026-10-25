# SIII attendance bridge — synthetic validation and physical gate

Native account/session authority remains in Ktor. Configured SIII tenants resolve actual teacher assignments, enrollment and dated occurrences through the live signed manifest and `SiiiSectionReader`. No native classroom/group is fabricated from an external name.

Sessions persist `academicSource=SIII`, institution, section ID and exact source version. Each discovery, confirmation and teacher/admin roster rechecks source membership, native identity/link, the assigned teacher, occurrence times and version. Projection lag remains 503. Withdrawal or reassignment removes authority immediately. Personal imports never grant attendance access.

Opening a pass commits recipients to the existing push outbox. Delivery remains asynchronous and token/session ownership is checked by the existing worker. The notification contains only its session action. Student confirmation uses the existing institutional school-day QR/network policy; no class QR is required. Confirmation stays `REVIEW_REQUIRED` pending review; it does not certify physical presence.

`SiiiAttendanceTest` exercises authenticated HTTP opening, role rejection, discovery, missing resource, outbox data, synthetic gateway delivery, confirmation without a class QR, teacher roster, revocation, tenant isolation and global administrative capability. `SiiiFullChainLiveContractTest` additionally uses real synthetic SIII HTTP schedule/enrollment mutations, PostgreSQL outbox delivery, signed Ktor ingress, Mongo snapshots, authenticated academic reads, Mongo attendance/presence/outbox persistence and an explicitly fake push gateway. It must execute without skips in SIII #109 before this receiver merges.

Android policy and next-class selection accept SIII absolute instants as well as existing native local times. API31/35 Academic and Management CI remain required.

No productive delivery, migration or credentials are enabled by this change. Physical QA must still verify real FCM token registration and provider acceptance, notification reception foreground/background/terminated, tap into the correct session, school QR/network checks, confirmation and teacher review with two actual Android devices. A fake push gateway or emulator never satisfies that gate.
