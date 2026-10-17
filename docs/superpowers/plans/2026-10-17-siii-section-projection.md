# Siii section snapshot receiver implementation plan

Approved scope: strict institutional snapshot receiver, dedicated Mongo persistence, internal authorized reader. Preserve all existing providers, mobile contracts, native classroom/attendance collections and roles. No Siii emitter, Android route or production enablement.

1. Add failing contract tests for strict DTO validation, source identity consistency, dated occurrences including 24:00, and semantic canonicalization. Implement the closed v1 DTO and `group:v1` adapter.
2. Add Mongo CAS tests for duplicate, stale, competing versions and same-version conflict. Implement dedicated snapshot repository and command handler; teach the existing worker to record typed permanent handler failures.
3. Add actual signed push → Mongo inbox → worker → snapshot → reader tests. Exercise live accounts, actual institutional identity links, cross-tenant collisions, missing identity, account/role/institution revocation, source withdrawal/teacher removal/tombstone, and crash-after-write recovery. Implement reader response models that cannot contain peer enrollments in a student response.
4. Wire only the strict adapter and concrete Mongo handler in ApplicationModule when the existing authenticated Mongo graph is configured. Unsupported types remain fail closed. Expose no new HTTP or mobile route.
5. Run focused tests with disposable loopback Mongo, existing worker/identity suites, shared contract tests and API build; scope ktlint to changed Kotlin paths and scan staged files for secrets. Obtain independent review, commit, push and open a separate PR after PR196.

Files: new `integrations/academic/siii/SiiiSectionSnapshot.kt`, `SiiiSectionProjection.kt`, `MongoSiiiSectionSnapshotRepository.kt`, `SiiiSectionReader.kt`; bounded edits in `InstitutionalEventProjectionWorker.kt` and `ApplicationModule.kt`; corresponding contract/Mongo tests and architecture documentation.

Validation commands use cached JDK17, Gradle with `--no-parallel --max-workers=1`, disposable `V12_TEST_MONGODB_URI=mongodb://127.0.0.1:27017`, and no production credentials. Source contract is documented in `docs/architecture/SIII_SECTION_SNAPSHOT_V1_PROPOSAL.md`.
