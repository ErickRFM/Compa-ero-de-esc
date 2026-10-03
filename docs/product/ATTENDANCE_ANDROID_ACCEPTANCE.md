# Android attendance acceptance

Branch/PR scope: student + teacher attendance UI on top of the occurrence, offline-outbox and signed-QR contracts.

## Security invariant

The Android client never creates a definitive VERIFIED verdict.

A student scan performs only:

1. select the active server session;
2. read a signed QR token;
3. persist one local outbox operation;
4. schedule delivery;
5. render PENDING until the server answers.

The UI may render **Verificada por el servidor** only when Room contains a reconciled record with:

- sync state = SYNCED;
- attendance status = VERIFIED.

The presentation policy has a unit test proving that a locally pending row cannot render as verified even if its status field is inconsistent.

## Development accounts

The local-only mock provider now supports both sides of the flow.

Student:

- username: `ana.lopez`
- password: `development-only`

Teacher:

- username: `elena.rios`
- password: `development-only-teacher`

These fixtures are rejected outside local development by the existing mock-provider production guard.

## Local QR setup

Set a local-only `ATTENDANCE_QR_SECRET` with at least 32 characters before running the teacher QR flow. Do not reuse `JWT_SECRET`.

## Teacher acceptance

1. Sign in as the teacher.
2. Open **Asistencia**.
3. The screen loads dated class occurrences for the current academic week.
4. Open a class occurrence.
5. Confirm one server attendance session is created/reused.
6. Confirm a QR appears.
7. Keep the screen open for at least 45 seconds.
8. Confirm the QR token rotates repeatedly without changing the attendance session.
9. Confirm the countdown never exceeds the server QR TTL.
10. From another device, register a student scan.
11. Refresh roster.
12. Confirm the student appears with the server-returned state.
13. For REVIEW_REQUIRED, verify/reject from the teacher UI.
14. Close the session.
15. Confirm QR rotation stops and the active session disappears.

## Student acceptance — online

1. Sign in as the student.
2. Open **Asistencia**.
3. Confirm only sessions for enrolled course/group pairs are visible.
4. Tap **Escanear QR**.
5. Grant camera permission.
6. Scan the current teacher QR.
7. Confirm the camera closes after one accepted token.
8. Confirm the immediate message says the pass was **saved**, not verified.
9. Confirm the local row first appears pending if reconciliation has not returned yet.
10. After sync, confirm the UI reflects the server result.
11. A VERIFIED server result may then display **Verificada por el servidor**.

## Student acceptance — offline

1. Start with an active teacher session and a visible QR.
2. Put the student device offline.
3. Open the scanner and capture the QR while it is valid.
4. Confirm the operation is stored locally.
5. Close/reopen the Android app.
6. Confirm the pending record is still visible.
7. Restore connectivity before the late-sync review window expires.
8. Confirm WorkManager sends the original operation id and original capture timestamp.
9. Confirm a late signed capture becomes REVIEW_REQUIRED / OFFLINE_LATE_SYNC instead of silently becoming VERIFIED.
10. Confirm retrying/reopening does not create a second canonical server attendance record.

## Camera cases

- permission granted;
- permission denied;
- close scanner without scanning;
- scan non-attendance QR: ignored;
- scan wrong attendance session: server rejects;
- rotate device while scanner is open;
- scan after app resumed from background;
- device with no camera: attendance screen remains usable, scanner cannot proceed.

## Teacher recovery

1. Open attendance.
2. Kill/restart the teacher app.
3. Sign in if necessary.
4. Open **Asistencia**.
5. Confirm `GET /attendance/sessions/mine` restores the teacher-owned live session.
6. Confirm QR rotation and roster actions resume.

## Required CI

Before merge:

- API compile/tests/smoke: green;
- Android compile: green;
- Android unit suites actually execute;
- lint errors: 0;
- debug APK: produced;
- minified release APK: produced;
- secret scan: green.

## Physical evidence still required

CI cannot certify:

- real camera focus in a classroom;
- glare/low-light QR readability;
- actual two-device teacher/student timing;
- OEM background scheduling behavior;
- process death during an offline outbox wait;
- real institutional teacher schedule integration.

Those remain physical/external acceptance gates.
