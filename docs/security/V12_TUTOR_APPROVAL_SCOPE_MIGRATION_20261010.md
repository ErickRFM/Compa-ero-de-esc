# V12 Tutor/Admin — Scope migration and security gate

Date: 2026-10-10. Applies to PR #139 and the `feat/v12-tutor-approval` branch.

## Invariants
- A student's group membership grants access only to the group's current `scopeRevision`. A period migration increments this revision even if the period label is later restored.
- Revision-zero memberships without an explicit generation are compatibility-only and cease authorizing immediately after an explicit group migration. No backfill or new grant is inferred from a historical row.
- An administrator must explicitly re-enroll a student using the current group scope and an MFA-protected operation. Mongo uses compare-and-swap on the old membership generation to prevent stale overwrites.
- Newly submitted excuses persist immutable `institutionId`, `periodId` and `groupScopeRevision`. A regular listing or review requires all three to match the current group; legacy/unknown or stale records remain retained in storage but are not disclosed.
- Published tutoring notes likewise require matching institution, current group revision and current student membership.
- Only one pending/active representative per group/position, and one such appointment per group/student. Mongo uses partial unique indexes; in-memory uses a write mutex. Revocations/declines release the slot.

## Staging/production migration (manual verification required)
1. Back up `academic_groups`, `academic_group_memberships`, `excuse_requests`, `group_representatives` and `tutoring_cases`. Do not export unredacted student data into CI or public artifacts.
2. Inventory records missing scope provenance. Correlate each with authoritative institution/group/period history. **Do not** automatically assume the group's present-day institution/period is the correct provenance for older records.
3. Inventory duplicate representatives by (group,position) and (group,student) for `PENDING`/`ACTIVE`; resolve duplicates with an auditable administrative decision before building unique indexes. Index creation intentionally fails closed if conflicts remain.
4. Document operator, timestamp, source of truth, old value, new value and approval for any historical backfill. Retain a migration ledger and rollback-ready backup.
5. For reused academic groups, explicitly re-enroll only current students via scoped administration. Do not copy every old membership into the new period.
6. Rehearse the migration against a sanitized staging database; validate index creation and retry behavior with concurrent writes, then deploy after an approved maintenance window if required.
7. Validate negative cases: stale membership denied, historical excuse hidden, cross-institution review denied, duplicate representative conflict, account revocation invalidates access.

## Deliberate limits
- No automatic historical excuse migration or deletion is performed by the API.
- Current views intentionally hide historical records whose scope cannot be proven. A separate tightly authorized archive/export process requires its own security review.
- Multi-document staff approval may need reconciliation if a process stops between granting a tutor role and writing assignments. No claim of a distributed transaction is made.
- Release signing, live institutional integration, real email delivery and physical-device QA remain external gates.

## CI gate
Run the complete API/Android CI for the final branch SHA, including non-skipped MongoDB concurrency tests, before merging. A passing unit test suite is not proof of production data migration or security certification.
