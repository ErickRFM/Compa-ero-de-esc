# UPTlax identity integration

## Boundary

The mandatory path remains:

```
Android -> Companion API -> IdentityProvider -> UPTlax adapter -> Institution
```

Android must never hold UPTlax integration credentials or call an institutional service directly.

## Current first-access semantics

Until the platform has a persistent user/account store and a real claim-verification service, first access is implemented as institutional validation rather than a second password system:

1. user enters matrícula or employee/institutional ID;
2. user supplies the institutional credential required by the configured provider;
3. the API delegates validation to `IdentityProvider.authenticate`;
4. the provider returns external identity, display name, email and roles;
5. the API issues a Companion session token;
6. Android stores only the Companion session token.

The institutional password is not persisted by Companion.

## Production provider requirement

Mocks remain forbidden in staging/production. A real UPTlax adapter must implement the existing provider boundary and be selected by environment configuration before a real-school pilot.

## Future persistent account claim

When a database-backed Companion account lifecycle is introduced, persist only identifiers and platform metadata such as:

- Companion user id;
- institution id;
- external identity id;
- role mapping;
- institutional email;
- status;
- created/updated timestamps;
- last institution sync timestamp.

Do not persist the upstream password.
