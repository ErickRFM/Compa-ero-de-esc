# Proposed Siii academic section snapshot v1

Status: receiver contract approved on 2026-10-17; implementation evidence is recorded separately. Base is read-adapter commit `ec88ad509cbb47aaa2f5308fe6f75ccf382fe0ab`. Production enablement is prohibited until the source emitter and reconciliation flow are tested.

## Scope and boundaries

One complete institutional section snapshot contains its subject/term, actual assigned teachers, enrollment roster, and persisted dated occurrences. Store it in a dedicated Mongo collection under `(tenantId, source=siii, sectionExternalId)`, never in native classroom/group/manual-schedule collections. Reuse the existing signed inbox, worker leases, retries, source-version ledger, reconciliation, and current institutional identity resolver.

This block adds the strict payload contract, `group:v1` adapter, Mongo snapshot repository/CAS handler, authorized reader service, composition wiring for the supported contract, and real Mongo pipeline tests. Siii transactional outbox/emitter, an Android API route, mobile consumption, native attendance/QR, account creation and role changes remain outside this block until separately implemented and tested.

## Existing envelope

Keep `InstitutionalPushEvent` unchanged: `schemaVersion=1`, `entityType=group`, `externalId=<Siii section UUID>`, immutable eventId, positive monotonic sourceVersion, occurredAtEpochSeconds, operation and payload. Authentication supplies tenant/credential/scopes; the payload must not override tenant. Only `group:v1` events with the exact contract discriminator below are supported by this adapter. Other contracts remain unsupported.

Upsert is a complete replacement snapshot, never a partial merge. Delete has the existing required empty payload and writes a versioned institutional tombstone. A withdrawn enrollment or cancelled occurrence is represented explicitly inside the next upsert snapshot.

## Strict payload

Required fields (all object fields closed; missing required or unknown fields fail):

```text
contract: "siii.academic_section_snapshot.v1"
sectionId: UUID == envelope.externalId
sectionCode: Siii section code
status: open | closed | archived
timezone: valid IANA zone from Siii institutions.timezone
term: { sourceId: UUID, code: string, startsOn: ISO date, endsOn: ISO date }
subject: { sourceId: UUID, code: string, name: string, credits: integer 0..30 }
teachers: [{ sourceId: UUID, externalId: employeeNumber, fullName: string }]
enrollments: [{ sourceId: UUID, studentSourceId: UUID,
                studentExternalId: enrollmentNumber, fullName: string,
                status: enrolled | withdrawn }]
occurrences: [{ sourceId: persisted occurrence UUID, patternSourceId: UUID,
                date: ISO date, startsAt: UTC instant, endsAt: UTC instant,
                status: SCHEDULED | CANCELLED | ONLINE,
                room: { sourceId: UUID, code: string, building: string,
                        campusSourceId: UUID, campusName: string } }]
```

IDs/names are bounded, nonblank and without control characters; UUIDs must be canonical lowercase strings. Section, room and identity codes match Siii uppercase codes of 2..32 characters; term/subject codes are 2..25. Names are 3..160 for catalog fields, 2..241 for combined person names, and building is 2..120. Teachers, enrollment IDs and occurrence IDs must be unique. Historical withdrawn rows may share a student, but there is at most one enrolled row per student and UUID/external identity/name references must remain consistent. Roster cap is 500 and occurrence cap is 3000, with the existing 1 MiB signed-body cap still enforced. These are receiver bounds, not a claim that Siii's full history is bounded; an oversized section fails explicitly and an emitter must not silently truncate it. Term end must follow or equal start (Siii has no term-duration maximum). Every occurrence date lies within the term, start precedes end, and local start/end match a same-day minute schedule or an exact next-day midnight end under `timezone`. This preserves Siii endMinute=1440 without guessing UTC or converting to 23:59. Empty roster/occurrences are valid complete snapshots. An open section requires at least one actual assigned teacher.

Source cross-check: Siii `db/schema/registry.ts`, `db/schema/schedule.ts`, `src/modules/registry/validation.ts`, `src/modules/scheduling/validation.ts` and `src/modules/academic/catalog-validation.ts` supply these status/code/credit/date/time vocabularies. No student or teacher source status is inferred from native roles.

Siii section UUID is the offered course ID; academic-course UUID belongs to subject metadata. Enrollment number and employee number remain the existing institutional link keys, with immutable Siii UUIDs carried separately. Do not derive or reuse native group/classroom IDs. Siii persisted occurrence IDs remain stable across room/state changes; do not substitute the hash produced by `AcademicOccurrenceProjection`.

## Identity and reader authorization

Recommended policy: persist authenticated institutional snapshots independently of whether every roster member has joined Compañero. These records contain external identities and confer no account, role, or native membership. `group:v1` commands therefore require no bulk native identity creation or whole-roster link resolution. The existing resolver is reused at **every reader call** for the actual requesting identity. Missing student links deny that student's read without blocking everyone else's section snapshot.

The caller must already be authenticated through the existing native session authority; no public route is added in this block. The reader additionally requires an enabled institution and a current verified account through existing live-authority checks. It then resolves the requested external student/teacher identity using `InstitutionalIdentityLinkService.resolve`, compares the resolved account ID with the actual caller, and checks institution plus the actual current matching role. It never trusts role/source/tenant claims supplied inside the snapshot or request body.

- Student view requires that exact external identity to have an enrolled row in the latest open section. Return only that enrollment and section/teacher/occurrence data; omit every other roster identity.
- Teacher view requires the exact currently linked teacher to be assigned in the latest section snapshot. Only that authorized teacher view may return the institutional roster. An absent/revoked account, removed role, disabled institution or wrong tenant denies the read immediately. Session revocation remains enforced at the existing authentication boundary; its end-to-end reader coverage must be added with the future API route.
- Tombstones/archived sections are unavailable to these initial schedule readers. Other historical/grade use cases need explicit future policy. Return only sourceVersion and syncedAt metadata; never expose raw stored payload, digest, credential IDs, event provenance or peer identities through a student view. No native write authority follows from a snapshot.

Local account/role/institution revocation is checked live. A source withdrawal, removed teacher assignment or source identity deactivation is only known after a newer signed snapshot/tombstone arrives. Ktor cannot infer source revocation from its last snapshot. The Siii sender must publish updated membership and withdraw/tombstone sections when source identities lose access; sender reconciliation must be tested before production activation.

The actual Android route remains pending; tests call the real reader service with current verified fixture actors. Existing `NativeAcademicProjection` and source-isolation behavior remain unchanged.

## Durable application and crash recovery

Persist a unique tenant/source/section identity, sourceVersion, eventId, canonical payload digest, validated payload or tombstone, and provenance timestamps. Canonical semantics use validated typed values, sorted object keys and source-ID-sorted unordered collections; JSON field order and equivalent UTC instant representations do not change the digest. An atomic CAS/upsert allows only a strictly newer sourceVersion. Exact same-version/same-content retries succeed without duplicate effects; older versions never overwrite a newer target. Same-version conflicting content is a permanent failure and never mutates the target, with no infinite retry (the inbox already rejects version/body conflicts).

Only return from the command handler after the target write has completed. Let the existing worker acknowledge the inbox afterward. If the process crashes after target write but before acknowledgment, the reclaimed event sees the same persisted version/content, applies idempotently and completes the original receipt. Target CAS also fences a stale worker whose lease was lost; never rely on lease ownership alone for write ordering.

Composition must instantiate concrete adapter/handler/reader only with the configured Mongo/auth graph and the strict supported contract. Existing unsupported outcomes remain for all other entity/version/payload combinations. Source credentials must be explicitly provisioned for the Siii institution; no implicit tenant mapping is introduced.

## Required acceptance evidence

Real local Mongo and the actual signed push service: HMAC/nonce authentication → receipt → worker → persisted snapshot → authorized reader. Cover duplicate delivery, a newer version winning before an older event, wrong-tenant ID collisions, missing identity, account/role/institution revocation, teacher removal, student withdrawal, cancellation/tombstone, invalid time/zone/IDs/unknown fields, and crash-after-write retry. Student reads must prove no peer roster leakage. No production data or writes are required.

Siii must later assign sourceVersion and insert the outbox event in the same transaction as the academic mutation/audit. Its delivery worker must reuse event/body/version across retries while signing each attempt with a fresh timestamp and nonce. Until that sender and a mobile API flow are exercised, this block proves only the receiver/projection/reader slice.
