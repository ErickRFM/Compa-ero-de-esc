# Academic model v2

## Purpose

The first student slice renders a recurring weekly schedule. Attendance requires a stronger identity: one concrete class meeting on one calendar date.

The v2 model therefore separates:

```
Course
  -> Group
     -> RecurringSchedulePattern
        -> ClassOccurrence
```

Attendance must bind to a `ClassOccurrence`, not directly to a course/group pair.

## Concepts

### AcademicTerm

Normalized term metadata. The existing provider `term` string remains supported while adapters migrate toward structured terms.

### RecurringSchedulePattern

The weekly rule:

- group;
- weekday;
- start/end local time;
- classroom.

It is not attendance evidence because it represents many weeks.

### ClassOccurrence

A dated meeting:

- deterministic occurrence id;
- pattern id;
- calendar date;
- local start/end;
- teacher;
- room;
- status;
- change metadata.

Statuses:

- SCHEDULED
- CANCELLED
- RESCHEDULED
- ONLINE

### Schedule changes

The model can preserve explicit metadata for:

- time changes;
- room changes;
- teacher changes;
- cancellation;
- move online.

The current mock provider has no override feed, so generated occurrences begin as `SCHEDULED` with no changes. Real-provider adapters can add override data later without changing the attendance contract.

## Stable ids

Pattern and occurrence ids are deterministic SHA-256-derived identifiers from normalized schedule identity.

A pattern remains stable across weeks. An occurrence differs by date.

This gives attendance one stable reference for idempotency, audit and dispute reconstruction.

## API

Existing endpoints remain unchanged:

- `GET /academic/load`
- `GET /academic/schedule`

New versioned endpoint:

`GET /academic/schedule/v2?weekOf=YYYY-MM-DD`

The caller must provide one date inside the desired week. The response is Monday-Sunday and contains dated occurrences.

## Migration rule

The Android client can keep using the v1 weekly schedule while v2 is introduced.

No existing F1 screen is migrated until:
- v2 backend tests are green;
- offline cache migration is designed;
- real provider mapping is known.

This prevents attendance work from destabilizing Hoy/Agenda.
