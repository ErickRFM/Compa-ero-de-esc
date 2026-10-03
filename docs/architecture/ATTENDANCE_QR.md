# Attendance dynamic QR

## Role in the attendance model

QR is evidence, not the attendance record itself.

The server still decides attendance from:

- authenticated student identity;
- enrollment;
- dated class occurrence;
- active attendance session;
- idempotent operation id;
- signed QR evidence when supplied.

## Token format

QR tokens are short-lived HMAC-SHA256 values:

```
v1.<session-id-b64>.<nonce-b64>.<issued-at>.<expires-at>.<signature-b64>
```

The payload contains no:

- student id;
- student name;
- email;
- location;
- attendance verdict.

The signing secret is `ATTENDANCE_QR_SECRET`, separate from `JWT_SECRET`.

## Rotation

Current policy:

- maximum token TTL: 25 seconds;
- teacher UI should rotate after 15 seconds;
- token expiration never extends beyond session closure.

A screenshot therefore becomes stale quickly.

The nonce changes on every issue call. The nonce is not globally single-use because one displayed class QR is intentionally scanned by many enrolled students.

## Verification outcomes

### Current valid token

Signed token + correct session + device capture timestamp inside signed window:

`VERIFIED / QR_VALID`

### Invalid signature or payload

`REJECTED / QR_INVALID`

### Token belongs to another session

`REJECTED / WRONG_SESSION`

### Expired evidence

If the attempt reaches the server after token expiry but its local capture timestamp was inside the signed window, it is not silently promoted to verified.

It becomes:

`REVIEW_REQUIRED / OFFLINE_LATE_SYNC`

This preserves offline attempts without allowing an old screenshot to create a definitive verified record.

### No QR token

Base attendance remains valid:

`LIKELY / IDENTITY_SESSION_TIME`

This keeps the core attendance domain usable before camera/QR scanning is available.

## Server endpoint

Teacher/staff QR generation:

`POST /attendance/sessions/{sessionId}/qr`

Teacher ownership is enforced. Administrative roles may act across ownership where existing attendance authorization allows it.

If `ATTENDANCE_QR_SECRET` is not configured, QR generation/verification is unavailable but base attendance remains usable.

## Security boundary

The client never receives the signing secret.

The client only receives signed short-lived evidence.

The API compares signatures using a constant-time byte comparison.

QR verification does not replace JWT authorization, enrollment checks, session ownership, session timing or attendance idempotency.

## Android scanning

Camera integration is a separate presentation/device task.

The scanner must:

1. read the token;
2. bind it to the selected/active attendance session;
3. enqueue the attempt locally before network delivery;
4. reuse the same operation id during retries;
5. never convert a QR scan directly into a local `VERIFIED` verdict.

Only the server response determines the final attendance state.
