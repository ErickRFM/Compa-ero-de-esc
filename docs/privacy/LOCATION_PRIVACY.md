# Location privacy

## Current state

**No location is collected. None.**

There is no `core:location` module, no location permission in the manifest, no
GPS or Wi-Fi or BLE code anywhere in the repository. There is nothing to
disable, because nothing was built.

This document exists now, before the code, on purpose. Location decisions are
hard to reverse: once a database of location history exists, deleting it is
the only way to honour a promise made afterwards.

## Why location is treated differently

Every other module in this project can be built, reviewed and deleted on
engineering merit. Location cannot. It is the one input that turns a study
aid into a surveillance record of a minor's movements, and it carries legal
requirements that vary by country and by age.

The threshold for adding it is therefore higher, and it is written down before
anyone is tempted to lower it.

## Why attendance may need it at all

The honest reason: proving a student is physically present at a scheduled
activity. A QR code, a teacher confirmation, or the student's own report could
each do the same job, and each is cheaper in privacy terms.

Location is proposed because it is convenient and largely passive. That is also
its problem: the same convenience is available to a school for purposes the
student did not agree to.

## Conditions before `core:location` is created

All of these must be answered and written down. None is satisfied today.

1. **Purpose, in one sentence.** Not "for attendance". What specifically, and
   why nothing cheaper achieves it.
2. **A cheaper alternative has been rejected in writing.** With the reason.
3. **Sampling and retention stated as numbers.** How often, how accurate, how
   long, and what is deleted and when.
4. **Who can see the data.** Students, teachers, parents, administrators.
   "Whoever has the app" is not an answer.
5. **Consent that can be withdrawn**, and what happens to already-collected
   data when it is.
6. **The legal basis**, given the target country and the student's age.
7. **The offline behaviour.** A student in a basement must not be forced to
   grant location to mark attendance.

## Constraints, if it is ever approved

These are assumed in advance.

### Collection

- Sampled only at the moment of an attendance action, never continuously.
- Never in the background.
- No geofencing, no dwell-time tracking, no movement history.
- Coarse accuracy is preferred. A classroom identifier is the useful signal;
  precise coordinates are not required.
- Never collected for any purpose other than the one in the record above.

### Retention

- Raw coordinates deleted as soon as the attendance record exists.
- If a proximity record is kept, a short fixed window, and the number is
  written in this document before implementation.
- Deletion is automatic and verifiable, not a manual cleanup.

### Access

- A student can see their own record and can delete it.
- A teacher sees presence, not a location trail.
- A parent sees the attendance outcome, not raw coordinates.
- Access to raw coordinates is logged.

### On device

- Location is not written to the cached database.
- Not included in cloud backup; backup is already disabled app-wide.
- Cleared on sign-out.

## Prohibited without a new decision record

- Background location
- Continuous tracking
- Geofencing
- Selling, licensing or sharing location data with anyone
- Using location for anything other than the recorded purpose
- Retaining raw coordinates "in case it is useful later"

## What is deliberately not built

| Not built | Note |
|---|---|
| `core:location` module | Blocked on the seven conditions above |
| `ACCESS_FINE_LOCATION` permission | Not in the manifest |
| `ACCESS_BACKGROUND_LOCATION` | Not in the manifest, and not planned |
| Wi-Fi / BLE scanning | Never planned; high privacy cost, unproven value |
| Attendance QR codes | Deferred; different privacy trade-off, not a location one |

## Related

- [Threat model](../security/THREAT_MODEL.md)
- [Security model](../security/SECURITY_MODEL.md)
- [Roadmap](../product/ROADMAP.md)
- [ADR-001: module boundaries](../architecture/ADR-001-MODULE-BOUNDARIES.md)
