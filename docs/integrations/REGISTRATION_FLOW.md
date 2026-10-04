# First access / registration flow

## Goal

Give a student or staff member a visible registration/activation path without creating an insecure parallel identity database.

## Current flow

```
Login
  -> Activar acceso
  -> matrícula / institutional ID + institutional credential
  -> Companion API /auth/login
  -> IdentityProvider
  -> institutional identity + roles
  -> Companion JWT
  -> role-aware product
```

This intentionally reuses the existing authenticated identity boundary. At the current architecture stage, a separate `/register` endpoint would either duplicate the same institutional check or pretend a persistent account lifecycle exists when it does not.

## Role authority

The user never chooses Student/Teacher/Admin in the UI. Roles come from the provider and are mapped by the server.

## Academic synchronization

Identity and academic data remain separate capabilities. After authentication, Hoy/Agenda load through the academic repository/provider using the authenticated identity. Registration must not make Android call a school timetable API directly.

## Next backend phase

A real persistent account claim flow should be added only together with:

- user/account repository;
- claim verification policy;
- revocation/session lifecycle;
- rate limits and anti-enumeration behavior;
- real UPTlax provider integration;
- audit events.
