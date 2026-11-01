# Mongo TLS readiness recovery — 2026-11-01

Deployment a02fe20022b698a9c7daa203f919599907cd0d2c passed all code gates but its replacement Render instance repeatedly returned /ready 503. Atlas remained accessible. Logs showed ClosedChannelException in TlsChannelStreamFactoryFactory.SelectorMonitor at socket registration. No Mongo credentials, roles, network access rules or application records were changed.

The 5.13.0 sources register pending sockets before removing them from the queue. A timeout may close one first; registration then throws and the closed entry remains queued. Reviewing the actual 5.14.0 sources artifact found the same reachable ordering, so upgrading was not treated as a proven fix.

Mitigation applied to the existing Render service: MONGODB_CONNECT_TIMEOUT_MS=30000 and MONGODB_SERVER_SELECTION_TIMEOUT_MS=30000, merging only these keys and starting a fresh process. /ready returned HTTP 200 with Mongo up after deploy dep-dbjdg5rbc2fs73cgrc8g. The independent readiness probe deadline remains 3 seconds. This is a mitigation, not proof the vulnerable selector cannot recur.

The code correction selects Mongo's supported Netty transport, avoiding that TLS selector implementation. Its optional Netty handler dependency is explicitly supplied and aligned by the 4.2.19.Final BOM. TLS, certificate validation, hostname checks, URI options, cancellation and request budgets remain enforced. No insecure SSL option or trust override is introduced. The QA Blueprint carries the same cold-start connection budgets.

Validation: the five baseline MongoConnectionHealthTest cases passed with the original transport. The first post-change run could not download four official Netty jars because Maven reads timed out; this was an infrastructure failure before compilation, not a passing test. Final local/CI results and deployed-head readiness must be checked before merging or calling the correction complete.

References: [MongoDB asynchronous TLS guidance](https://www.mongodb.com/docs/languages/java/reactive-streams-driver/current/security/tls/), [upstream selector discussion](https://github.com/mongodb/mongo-java-driver/pull/1981), [Netty 4.2.19 release](https://netty.io/news/2026/10/06/4-2-19-Final.html).

The existing Render service had no HTTP health-check path configured. Its TCP-only deploy status did not establish database readiness. Setting /ready as the deployment health check remains a separate configuration check; the authenticated connector does not expose that setting and the dashboard did not finish loading during the attempted inspection.

QA native account verification, first administrator MFA, official source publication, migration 0027 activation and physical FCM remain distinct from this production connectivity fix.
