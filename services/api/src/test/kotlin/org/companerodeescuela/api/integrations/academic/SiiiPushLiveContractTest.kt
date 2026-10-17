package org.companerodeescuela.api.integrations.academic

import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.ResponseSent
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.bson.Document
import org.companerodeescuela.api.integrations.academic.siii.MongoSiiiSectionSnapshotRepository
import org.companerodeescuela.api.integrations.academic.siii.SiiiSectionEventAdapter
import org.companerodeescuela.api.integrations.academic.siii.SiiiSectionProjectionHandler
import org.companerodeescuela.api.integrations.push.InstitutionalEventProjectionWorker
import org.companerodeescuela.api.integrations.push.InstitutionalProjectionIdentityResolver
import org.companerodeescuela.api.integrations.push.InstitutionalPushAuthenticator
import org.companerodeescuela.api.integrations.push.InstitutionalPushService
import org.companerodeescuela.api.integrations.push.InstitutionalSecretProtector
import org.companerodeescuela.api.integrations.push.IntegrationCredential
import org.companerodeescuela.api.integrations.push.MongoInstitutionalPushRepository
import org.companerodeescuela.api.integrations.push.institutionalPushRoutes
import org.companerodeescuela.api.plugins.ApiJson
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.Collections
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Real Node sender -> loopback Ktor route -> disposable Mongo. Never opens a deployed origin. */
@EnabledIfEnvironmentVariable(named = "COMPANION_PUSH_PROBE_PROJECT_ROOT", matches = ".+")
@Timeout(120)
class SiiiPushLiveContractTest {
    @Test
    fun `real Siii sender signs immutable retries accepted once by Ktor and institutional projection`() =
        runBlocking {
            require(System.getenv("V12_TEST_MONGODB_URI") == "mongodb://127.0.0.1:27017") {
                "Push probe requires the disposable loopback Mongo lab"
            }
            MongoClient.create("mongodb://127.0.0.1:27017").use { client ->
                val db = client.getDatabase("v12_test_${UUID.randomUUID().toString().replace("-", "")}")
                try {
                    probe(db)
                } finally {
                    db.drop()
                }
            }
        }

    private suspend fun probe(db: MongoDatabase) {
        val tenantId = UUID.randomUUID().toString()
        val sourceInstitutionId = UUID.randomUUID().toString()
        val keyId = "probe-${UUID.randomUUID()}"
        val secret = randomKey()
        val inbox = MongoInstitutionalPushRepository(db, InstitutionalSecretProtector(randomKey()))
        inbox.putCredential(IntegrationCredential(keyId, tenantId, secret, setOf("group:write"), true, null), Instant.now())
        val statuses = Collections.synchronizedList(mutableListOf<Int>())
        val observer =
            createApplicationPlugin("ObserveSyntheticPushStatus") {
                on(ResponseSent) { call -> statuses.add(call.response.status()?.value ?: 0) }
            }
        val server =
            embeddedServer(CIO, host = "127.0.0.1", port = 0) {
                install(ContentNegotiation) { json(ApiJson) }
                install(observer)
                routing { institutionalPushRoutes(InstitutionalPushAuthenticator(inbox, inbox), InstitutionalPushService(inbox), inbox) }
            }.start(wait = false)
        try {
            val port =
                server.engine
                    .resolvedConnectors()
                    .single()
                    .port
            val report = runNodeProbe("http://127.0.0.1:$port", sourceInstitutionId, tenantId, keyId, secret)
            assertEquals(
                listOf("accepted", "accepted"),
                report
                    .getValue("statuses")
                    .jsonArray
                    .take(2)
                    .map { it.jsonPrimitive.content },
            )
            withTimeout(5_000) { while (statuses.size < 6) delay(10) }
            assertEquals(listOf(202, 200, 401, 200, 401, 401), synchronized(statuses) { statuses.toList() })
            val rejected = report.getValue("negativeResults") as JsonObject
            assertEquals(setOf("stale", "replay", "wrongKey"), rejected.keys)
            assertTrue(rejected.values.all { it.jsonPrimitive.content == "401" })
            val eventId = report.getValue("eventId").jsonPrimitive.content
            require(eventId.length in 1..128)
            val bodySha256 = report.getValue("bodySha256").jsonPrimitive.content
            require(bodySha256.matches(Regex("[a-f0-9]{64}")))
            val events = db.getCollection<Document>("institutional_integration_events")
            assertEquals(1L, events.countDocuments())
            val receipt = assertNotNull(events.find(Document("tenantId", tenantId).append("eventId", eventId)).firstOrNull())
            assertEquals(bodySha256, receipt.getString("payloadSha256"))
            assertNull(events.find(Document("tenantId", sourceInstitutionId)).firstOrNull())
            val sectionId = receipt.getString("externalId")
            assertEquals(report.getValue("sectionId").jsonPrimitive.content, sectionId)
            val sections = MongoSiiiSectionSnapshotRepository(db)
            val worker =
                InstitutionalEventProjectionWorker(
                    inbox,
                    InstitutionalProjectionIdentityResolver { _, _ -> error("Snapshot must not invent native identity authority") },
                    SiiiSectionProjectionHandler(sections),
                    listOf(SiiiSectionEventAdapter()),
                )
            assertTrue(worker.processNext(tenantId, "group:v1"))
            assertFalse(worker.processNext(tenantId, "group:v1"))
            val projected = assertNotNull(sections.find(tenantId, sectionId))
            assertEquals(eventId, projected.eventId)
            assertNotNull(projected.snapshot)
            assertNull(sections.find(sourceInstitutionId, sectionId))
            val completed = assertNotNull(events.find(Document("tenantId", tenantId).append("eventId", eventId)).firstOrNull())
            assertEquals("projected", completed.getString("projectionStatus"))
            assertEquals(0L, db.getCollection<Document>("platform_accounts").countDocuments())
        } finally {
            server.stop(0, 5_000)
            secret.fill(0)
        }
    }

    private fun runNodeProbe(
        origin: String,
        sourceInstitutionId: String,
        tenantId: String,
        keyId: String,
        secret: ByteArray,
    ): JsonObject {
        val project = Path.of(requireNotNull(System.getenv("COMPANION_PUSH_PROBE_PROJECT_ROOT"))).toAbsolutePath().normalize()
        require(Files.isRegularFile(project.resolve("tests/support/companion-push-live-probe.ts"))) { "Synthetic sender probe is missing" }
        val output = Files.createTempFile("siii-push-probe-", ".log")
        try {
            val builder =
                ProcessBuilder(
                    System.getenv("COMPANION_PUSH_PROBE_NODE")?.takeIf(String::isNotBlank) ?: "node",
                    "--import",
                    "tsx",
                    "tests/support/companion-push-live-probe.ts",
                ).directory(project.toFile()).redirectErrorStream(true).redirectOutput(output.toFile())
            // Forward only runtime paths, never unrelated database/API credentials from the parent lab.
            val runtimeEnvironment =
                builder.environment().filterKeys {
                    it.uppercase() in setOf("PATH", "SYSTEMROOT", "WINDIR", "TEMP", "TMP", "USERPROFILE", "HOME")
                }
            builder.environment().clear()
            builder.environment().putAll(runtimeEnvironment)
            builder.environment().putAll(
                mapOf(
                    "COMPANION_PUSH_PROBE_ORIGIN" to origin,
                    "COMPANION_PUSH_PROBE_SOURCE_INSTITUTION_ID" to sourceInstitutionId,
                    "COMPANION_PUSH_PROBE_TARGET_TENANT_ID" to tenantId,
                    "COMPANION_PUSH_PROBE_CREDENTIAL_ID" to keyId,
                    "COMPANION_PUSH_PROBE_SECRET_BASE64" to Base64.getEncoder().encodeToString(secret),
                ),
            )
            val process = builder.start()
            try {
                assertTrue(process.waitFor(45, TimeUnit.SECONDS), "Synthetic sender exceeded its time bound")
                assertEquals(0, process.exitValue(), "Synthetic sender failed; raw child output is deliberately suppressed")
                require(Files.size(output) in 1..65_536) { "Synthetic sender report exceeds its bound" }
                return try {
                    Json.parseToJsonElement(Files.readString(output)) as JsonObject
                } catch (_: IllegalArgumentException) {
                    throw AssertionError("Synthetic sender report is not valid JSON; raw child output is deliberately suppressed")
                }
            } finally {
                if (process.isAlive) process.destroyForcibly().waitFor(5, TimeUnit.SECONDS)
            }
        } finally {
            Files.deleteIfExists(output)
        }
    }

    private fun randomKey(): ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }
}
