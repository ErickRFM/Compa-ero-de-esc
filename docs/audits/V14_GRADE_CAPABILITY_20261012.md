# V14 — Verified grade publishing capability preflight

- New authenticated `GET /grading/capability` for TEACHER role reports whether a non-placeholder institutional grade gateway is registered.
- Android checks this endpoint before enabling the "Enviar a control escolar" button; unknown or unavailable readiness fails **closed**, not as a misleading "try" operation.
- Excel/CSV parsing, local preview, grading weights, and error diagnosis remain available if institutional publishing is unavailable.
- POST `/grading/sync` remains authoritative and preserves existing authorization and outcome reporting. A reported capability does **not** guarantee an actual successful publication; only the POST's response confirms it.
- Backend and Android regression tests cover unavailable/missing gateway and existing in-flight protection.

**External dependency:** the server currently wires an unavailable grade gateway. Real publication still requires the school's authorized API, cohort mapping and acceptance validation, and must not be claimed complete.
