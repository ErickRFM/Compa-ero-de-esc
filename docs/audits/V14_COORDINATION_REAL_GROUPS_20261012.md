# V14 — Real institution-scoped active groups in Coordination

The authenticated `GET /coordination/overview` returns a count of active, valid-period groups scoped to a live, active native COORDINATOR account's current institution. It never returns data from another tenant and never uses frontend role intent. Response is `no-store`.

Android uses a Hilt-injected repository and StateFlow ViewModel; it presents this verified count or an explicit unavailable/error state. A zero is shown only for a successful server response. Teacher attendance percentage and incidents remain intentionally unavailable because no authorized aggregation has been integrated; do not invent those metrics.

Backend unit test covers tenant isolation, active groups, expiry, future start, invalid scope. Real production DB latency and hardware QA are not covered by this PR. Start base: `03d2069c4ac1aeaf7daf6227a4ff8b814264ac50`.
