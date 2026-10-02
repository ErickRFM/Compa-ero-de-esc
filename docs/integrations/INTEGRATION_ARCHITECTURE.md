# Integration architecture

This is the most important architectural document in the repository. Almost
every long-term risk in this product comes from coupling to a school system we
do not control.

## The rule

```
Android  ──▶  Our API  ──▶  Provider interface  ──▶  Adapter  ──▶  Institution
                                                     │
                                                     ▼
                                              Mapper  ──▶  Our domain
```

**The Android app never talks to an institutional system.** Every credential,
every institutional quirk, every rate limit and every failure mode lives
behind our API.

## Why

1. **Credentials.** A school-system password on a student phone is a
   credential leak waiting for a lost device or a rooted handset.
2. **Quirks do not spread.** Spanish weekday names, times without dates, ids
   that are really course codes, `null` meaning two different things. If an
   upstream field reaches a screen, one institution's data model becomes the
   product's data model, and the second institution is then unsupported.
3. **It is testable.** A provider interface can be mocked, so the platform
   runs end to end with no school system.
4. **It is replaceable.** Schools change. A provider interface is the seam.

## Provider interfaces

Each capability is a **separate** interface, not one large one. A school with
no library system must not require a library adapter.

| Interface | Capability | Status |
|---|---|---|
| `AcademicProvider` | Students, courses, enrollments, timetable | Mock |
| `IdentityProvider` | Institutional authentication | Mock |
| `LearningProvider` | LMS: assignments, grades, materials | Mock |
| `LibraryProvider` | Loans, holds, catalogue | Mock |
| `EventsProvider` | Institutional calendar | Mock |
| `SyncProvider` | Change feed / delta sync | Mock |

All share `IntegrationProvider`, which identifies the provider and its
capabilities.

## The DTO boundary

Each adapter owns **its own** external DTOs. This is the rule that does the
real work.

```kotlin
// External DTO: upstream vocabulary, nothing more
@Serializable
data class ExternalStudent(
    val externalId: String,
    val fullName: String,
    val institutionalEmail: String? = null,
)
```

```kotlin
// Our domain: our vocabulary
data class Student(val person: Person)
```

An external DTO never appears in a domain type, a repository signature, or a
response body. If `ExternalStudent` reached a ViewModel, the boundary has
leaked and the next institution to use a different field name will break the
app rather than one adapter.

### Mappers are total where they can be, and loud where they cannot

```kotlin
// Total: clean up, no way to fail
fun toStudent(student: ExternalStudent): Student = Student(
    person = Person(
        id = PersonId(student.externalId),
        displayName = student.fullName.trim(),
        institutionalEmail = student.institutionalEmail
            ?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
    ),
)
```

- Blank upstream values become `null`, never `""`.
- Emails are lowercased; display names are trimmed.
- The identity of the source is discarded. A domain type never carries
  "this came from system X", because nothing downstream should care.

Where a mapper cannot be total, it fails explicitly:

```kotlin
val error = assertFailsWith<IntegrationException> {
    AcademicMappers.toSchedule(ownerId, listOf(slot(weekday = "Someday")))
}
assertEquals(IntegrationException.Category.MALFORMED_RESPONSE, error.category)
```

An unrecognised weekday is a data problem, and silently dropping the class
would hide a broken integration from the user while looking like working code.

One deliberate exception: when a schedule slot references a course the API
cannot resolve, the slot is **kept** with a visible "Materia no disponible"
placeholder. Dropping a class from a student's timetable is worse than showing
it unresolved.

## Error categories

`IntegrationException.Category` separates failure kinds the caller must
treat differently.

| Category | Meaning |
|---|---|
| `NOT_FOUND` | The entity genuinely does not exist |
| `UNAUTHENTICATED` | Institutional credentials rejected |
| `FORBIDDEN` | Authenticated but not permitted |
| `RATE_LIMITED` | Upstream is throttling; retry later |
| `UPSTREAM_UNAVAILABLE` | Network or 5xx from the institution |
| `MALFORMED_RESPONSE` | Upstream returned data we cannot map |

`NOT_FOUND` is deliberately distinct from `UNAUTHENTICATED`: a student with a
valid session asking for a timetable that does not exist should not be logged
out.

## Mock providers

Mocks exist so the entire platform runs with no external dependency. They are
first-class implementations, not test doubles.

The safety property that matters:

```kotlin
// Called from Application.module, before plugins are installed.
ProviderRegistry.requireEnvironmentSatisfied(providerRegistry, settings.environment)
```

`staging` and `production` **throw** if any provider is a mock. This is
enforced at boot, not in a test, because the failure this prevents is serving
fabricated academic data to real students.

> This check was initially written and never called. See the
> [bug register](../quality/BUG_REGISTER.md).

`ProviderRegistryTest` covers the policy. The call from `module()` is what
actually enforces it, and the smoke test in the release checklist confirms the
service still starts locally.

## Adding a real provider

1. Implement the capability interface.
2. Create an `ExternalXxx` DTO in that adapter's own package. Do not reuse
   another institution's DTO.
3. Add a mapper from external to domain, total where possible, explicit-fail
   where not.
4. Add a `ProviderFactory` selected by configuration.
5. Add tests with a recorded sanitized fixture, including the malformed cases.
6. Register it in `ProviderRegistry`.
7. Confirm `requireEnvironmentSatisfied` passes with it selected.

Steps 5 and 7 are the ones that get skipped and then regretted. A real adapter
without a malformed-input test will meet malformed input in production.

## Related

- [System architecture](../architecture/SYSTEM_ARCHITECTURE.md)
- [API architecture](../architecture/API_ARCHITECTURE.md)
- [Security model](../security/SECURITY_MODEL.md)
- [Threat model](../security/THREAT_MODEL.md)
