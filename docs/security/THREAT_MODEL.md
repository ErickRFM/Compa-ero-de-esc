# Threat model

Scope: the foundation as it exists today, plus the risks that the foundation
is expected to have to survive when product features land.

## Assets

| Asset | Sensitivity | Why |
|---|---|---|
| Institutional credentials | Critical | Full access to a school system |
| Student identity and timetable | High | Reveals movement, associations, absences |
| Attendance records | High | Affects the student's academic record |
| Location history | High | Surveillable, easily misused |
| Session tokens | High | Full account access until revoked |
| Absence diagnostics | Low | Helpful, no personal data |

## Trust boundaries

```
┌─ Device (untrusted: lost, rooted, inspected) ─────────┐
│  App  ─▶ stored data, tokens, cached timetable          │
└────────────────────────┬───────────────────────────────┘
                         │ TLS
┌────────────────────────▼───────────────────────────────┐
│  Our API (trusted within our control)                   │
│    ├── institutional credentials  ◀── highest value    │
│    └── attendance records, sessions                    │
└────────────────────────┬───────────────────────────────┘
                         │ vendor-specific
┌────────────────────────▼───────────────────────────────┐
│  Institutional system (third party, low control)       │
└─────────────────────────────────────────────────────────┘
```

The device is the least trusted component. It is in a student's pocket.

## Threats and mitigations

### 1. Institutional credential exposure

**Threat.** A school-system password is stored on or sent to a student device.
The device is lost, stolen, or rooted.

**Mitigation.** Credentials live only in the API. The app authenticates to us,
never to the school. This is the single most important structural decision in
the project.

**Status.** Enforced by architecture. No credential code path exists on the
client because no client integration exists.

**Residual risk.** An API compromise exposes every school's credentials. This
is why per-school credential isolation matters once there is more than one
pilot.

### 2. Fabricated data served to real students

**Threat.** A misconfigured deployment serves mock academic data. A student
attends the wrong class, or a parent disputes an absence that was never
recorded.

**Mitigation.** `ProviderRegistry.requireEnvironmentSatisfied` throws at boot
in staging and production if any provider is a mock.

**Status.** Enforced and tested. This bug existed in reality: the check was
written and never called. See the [bug register](../quality/BUG_REGISTER.md).

### 3. Cleartext transport

**Threat.** Traffic intercepted on a school network, which is frequently open
or shared.

**Mitigation.** `network_security_config.xml` permits cleartext only for
loopback and emulator addresses. Everything else requires HTTPS.

**Status.** Enforced. Residual risk: a misconfigured base URL would fail
closed, which is the intended failure.

### 4. Device loss exposing cached data

**Threat.** A lost phone with a cached timetable and a session token.

**Mitigation.** Backup and device transfer are disabled. Cache encryption and
token storage are **not yet implemented**.

**Status.** Partial. `core:security` is required before real data is cached.

### 5. Over-collection of location

**Threat.** Attendance location sampled too often, too precisely, or kept too
long, turning a study aid into a surveillance tool.

**Mitigation.** The `core:location` module is **not created** until the
[privacy model](../privacy/LOCATION_PRIVACY.md) is agreed. There is no
location code in the repository today, so there is nothing to leak.

**Status.** Deferred by design. Deleting code is easier than deleting a
database of collected locations.

### 6. Upstream data poisoning or confusion

**Threat.** A compromised or misconfigured institutional system returns
malformed data, which the app renders as fact.

**Mitigation.** Mappers are total where possible and fail explicitly where
not; an unrecognised weekday raises `IntegrationException(MALFORMED_RESPONSE)`
rather than being silently dropped. Unresolved courses are shown visibly as
unresolved rather than hidden.

**Status.** Enforced and tested with malformed fixtures.

### 7. Denial of service on a school network

**Threat.** A shared, low-capacity network, or an upstream that is slow.

**Mitigation.** Ktor timeouts (10s connect, 30s request). `isRetryable()`
marks 5xx and network errors retryable and explicitly marks 401 **not**
retryable, so a failing client cannot hammer a rejecting server.

**Status.** Partially enforced. Rate limiting is not built.

### 8. Error messages leaking internals

**Threat.** A stack trace, connection string or DSN reaching a user.

**Mitigation.** Closed `ApiErrorCode`; unknown exceptions become a generic
`internal_error` with a `requestId`. `AppError` separates `userMessage` from
`technicalDetail`. Tests assert that no health response contains the connection
string and that no user message contains a brace.

**Status.** Enforced and tested.

### 9. Log exposure

**Threat.** Access logs containing a student's schedule or identifiers.

**Mitigation.** Call logging records method, path, status, duration and
request id. Body logging is off. `ValidationResult` never echoes a rejected
value.

**Status.** Enforced. Needs a redaction pass once real endpoints exist.

## Accepted risks

| Risk | Rationale |
|---|---|
| API compromise exposes credentials | Unavoidable given we must talk to the school. Mitigated by per-school isolation later |
| No certificate pinning | Adds a failure mode on school networks with odd CAs. Revisit only if threat modelling justifies it |
| Mock password constant in the repository | A fictional test account, refused outside local and development |
| No rate limiting yet | Phase 1, before any real user exists |

## Out of scope for now

- Social engineering of school staff
- Physical attacks on a device
- Supply chain compromise of third-party apps
- Coercion by a school or parent

## Review triggers

Revisit this model when: authentication lands, `core:location` is created,
offline caching of real data begins, or a second school is onboarded. The
second school is the real test of whether the provider boundary holds.
