# Attendance domain

## Core rule

Attendance is not a boolean and QR is not the verdict.

The canonical chain is:

```
Course
  -> Group
     -> ClassOccurrence
        -> AttendanceSession
           -> AttendanceAttempt
              -> AttendanceRecord
```

Every session is bound to one dated `ClassOccurrence`.

## Session identity

A teacher opens attendance for:

- one occurrence id;
- one occurrence date;
- one server-controlled time window.

The API resolves the occurrence against the teacher's academic schedule before creating a session.

One occurrence maps to one attendance session. This prevents two independent rosters for the same meeting.

## Student attempt

The app submits:

- session id;
- stable client `operationId`;
- device timestamp.

The server decides validity from:

- authenticated student identity;
- active server-side session;
- enrollment in the session course/group;
- idempotency rules.

The first phase records `LIKELY`, not `VERIFIED`, because identity + enrollment + an open session is useful evidence but is not yet the signed QR evidence planned for a later phase.

## Idempotency

Two independent uniqueness rules exist:

1. one attendance record per `sessionId + studentId`;
2. one global `operationId` per attendance write.

A retry with the same operation returns the canonical record.

A different operation for the same student/session still resolves to the same canonical record.

Reusing one operation id for another student/session is a conflict.

## States

Attendance record state:

- VERIFIED
- LIKELY
- REVIEW_REQUIRED
- REJECTED

Session state:

- OPEN
- CLOSED

Reason codes are enums, not free-form internal strings:

- IDENTITY_SESSION_TIME
- DUPLICATE
- OFFLINE_LATE_SYNC
- NOT_ENROLLED
- OUTSIDE_ALLOWED_WINDOW
- TEACHER_REVIEW
- SESSION_CLOSED
- WRONG_SESSION

## Authorization

Student:
- list their applicable active sessions;
- submit their own attempt.

Teacher:
- open a session only for a dated occurrence they teach;
- close/inspect/review sessions they opened.

Coordinator/admin/super-admin:
- may inspect, close or review across teacher ownership.

Authorization is enforced from JWT roles and again by session ownership in the service.

## Storage

MongoDB:

- `attendance_sessions`
- `attendance_records`

Unique indexes:

- `attendance_sessions.occurrenceId`
- `attendance_records.operationId`
- record `_id = sessionId:studentId`

Local development may use the in-memory repository.

Non-local environments require MongoDB before attendance routes are usable.

## API

- `POST /attendance/sessions`
- `GET /attendance/sessions/active`
- `POST /attendance/sessions/{id}/attempts`
- `POST /attendance/sessions/{id}/close`
- `GET /attendance/sessions/{id}/roster`
- `PATCH /attendance/records/{id}/review`

## Explicitly not implemented in this phase

- offline outbox;
- WorkManager retry;
- signed QR tokens;
- QR rotation;
- location/Wi-Fi/BLE evidence;
- automatic absence generation;
- push notifications.

Those are later phases and must not be mixed into the correctness of the base attendance record.
