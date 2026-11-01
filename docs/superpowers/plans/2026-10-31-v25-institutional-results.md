# V25 institutional results and certification

Initial heads: Compañero `0f31b12b52c018ed58414308da3d41872e0e5c45`; SIII `c5a5843dc7a50df0f5a97a3d8b6a3742cd4cbabe`. Main: `9081df0ce9a8958f831724f52fbba1d28222fef1` / `7150280f56594101d8f1ee96f8ec9cdc212e6f9a`.

1. Repair the live-contract workflow regression and verify its negative gates.
2. Add a monotonic attendance record revision with database CAS; retain the v1 public read contract and negotiate v2 for durable institutional acceptance.
3. Persist authorized accepted results and immutable revisions in SIII PostgreSQL using a versioned migration and tenant-scoped constraints. Reuse source revalidation and audit transactions. Never overwrite a newer correction or infer absence from missing samples.
4. Reuse institutional data for bounded, paginated student/teacher history and explicit metrics; preserve authority checks and cancellation semantics.
5. Verify unit, integration, live PostgreSQL/Mongo contract, Android and builds. Review before merging. Record actual deployment heads.
6. Execute QA account linking/publication only via existing authorized endpoints with MFA and proven isolation. Actual device/FCM certification requires configured Firebase and devices; synthetic evidence never substitutes for it.

Acceptance remains open until QA-UNIV student and teacher complete the same official occurrence, attendance is retained in both systems, and histories/metrics agree. Remote prerequisites are not waived by a passing build.
