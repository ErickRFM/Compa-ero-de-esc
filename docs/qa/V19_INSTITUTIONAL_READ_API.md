# V19 institutional academic HTTP reads

The native session resolves its own institution and the existing verified institutional identity link. Clients cannot choose an institution or external student/teacher identifier. `SiiiSectionReader` remains the authorization boundary for every projected section; student rosters are excluded from the HTTP response.

Endpoints (native authenticated student or teacher):

- `GET /academic/institutional/sections`: authorized official sections, subjects, groups, teacher names and dated occurrences.
- `GET /academic/institutional/sections/{sectionId}`: one section in the current source manifest; unknown/unassigned sections return 404.
- Existing `/academic/load`, `/academic/schedule`, `/academic/schedule/v2?weekOf=YYYY-MM-DD` use the same institutional service for explicitly configured tenants. Other tenants retain their existing native projection.

Server-only opt-in configuration: `SIII_ACADEMIC_AUTHORITY_TARGETS_JSON` maps each native institution ID to `{origin,sourceInstitutionId,keyId}`. Origins must be HTTPS origins without paths, query, userinfo or explicit ports. The source UUID and key reference must be explicit. The key references the already encrypted Ktor integration credential for that native institution with `group:write` authority. No credential is added to Android or committed here.

The Ktor client signs the source authority purpose with that credential. It accepts only the expected source institution, identity and contract, and checks a fresh manifest before and after reading exact Mongo snapshot versions. A source change or pending projection returns 503. Source revocation returns 403. Missing native links/source identities return 404; absent native sessions return 401. Source credential failures return 503 and do not invalidate the native user session. There is no fallback from failed institutional authority to native or imported enrollment.

`AcademicLoadResponse.institutionalSections` carries the official catalog even when a section has no schedule occurrences; `institutionalSource=SIII` distinguishes a valid empty institutional response. Schedule entries carry institutional provenance and exact effective dates. Personal Android imports remain in their separate store and never grant enrollment or attendance permission.

Tests: `SiiiAcademicRoutesTest` covers HTTP authentication, roles, tenant/identity scope, catalog data, no roster exposure, 404, empty results, source revocation, withdrawn enrollment, removed teacher, stale/missing projections and configuration guards. The cross-repository `SiiiFullChainLiveContractTest` additionally calls real HTTP `/academic/load` with native sessions and real signed SIII authority requests using only disposable PostgreSQL/Mongo data. The loopback endpoint resolver is supplied exclusively by that test harness; production configuration requires HTTPS.

This code does not apply migrations, create credentials, configure delivery, enable production flags or certify physical attendance/FCM. Production remains NO-GO until the SIII release preflight and controlled pilot gates are independently satisfied.
