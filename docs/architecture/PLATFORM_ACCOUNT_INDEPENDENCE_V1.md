# Platform Account Independence V1

## Decision

Compañero de Clase owns its primary user account and session lifecycle.

Institutional identity is optional. A school API, LDAP, SSO, or other upstream
provider may authenticate or enrich a user, but its availability must not be a
precondition for using the platform.

## Identity boundaries

- Platform account: email + platform password, profile, roles, session.
- Institutional identity: optional external identity used for synchronization.
- Academic data: may originate from school API, teacher, administrator, import,
  or manual user entry and must retain provenance.

## Teacher safety

Self-registration as teacher produces the `TEACHER_PENDING` role. This role is
not staff and does not grant teacher capabilities. Verification is a separate
workflow.

## Authentication order

1. Resolve a native platform account by email/id.
2. Verify its one-way password hash.
3. If no native account exists, optionally delegate to the configured
   institutional identity provider.
4. Mint the same platform JWT/session shape in either case.

This preserves existing institutional accounts while allowing the product to
operate independently.

## Password storage

Native account passwords are never stored in plaintext. The API stores a salted
PBKDF2-HMAC-SHA256 derivation with a per-account random salt. Institutional
passwords are not persisted by this flow.

## Next contracts

The next implementation waves are:
1. InstitutionalIdentity linking and synchronization.
2. Classroom + ClassMembership.
3. Expiring ClassInvite tokens and QR.
4. Teacher verification.
5. Avatar storage.
