package org.companerodeescuela.api.health

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import org.companerodeescuela.api.application.module
import org.companerodeescuela.api.test.FakeMongoConnection
import org.companerodeescuela.api.test.TestFixtures
import org.companerodeescuela.shared.contracts.HealthResponse
import org.companerodeescuela.shared.contracts.ReadinessResponse
import org.companerodeescuela.shared.contracts.ServiceStatus
import org.companerodeescuela.shared.contracts.VersionResponse
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class HealthRoutesTest {

    private val json = Json

    /**
     * Boots the real application module against a fake database, so these
     * tests cover routing, serialization, plugins and error handling together.
     */
    private fun apiTest(
        mongoReachable: Boolean = true,
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        application {
            module(
                settings = TestFixtures.settings(mongoUri = "mongodb://localhost:27017"),
                mongoConnection = FakeMongoConnection(reachable = mongoReachable),
            )
        }
        block()
    }

    @Test
    @DisplayName("GET /health returns the documented payload")
    fun healthEndpoint() = apiTest {
        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        val payload = json.decodeFromString<HealthResponse>(response.bodyAsText())
        assertEquals("companero-api", payload.service)
        assertEquals(ServiceStatus.UP, payload.status)
        assertEquals("0.1.0-test", payload.version)
        assertEquals("local", payload.environment)
        assertEquals(listOf("mongodb"), payload.dependencies.map { it.name })
    }

    @Test
    @DisplayName("GET /ready answers 200 when the database is reachable")
    fun readinessOk() = apiTest {
        val response = client.get("/ready")

        assertEquals(HttpStatusCode.OK, response.status)
        val payload = json.decodeFromString<ReadinessResponse>(response.bodyAsText())
        assertEquals(ServiceStatus.UP, payload.status)
    }

    @Test
    @DisplayName("GET /ready answers 503 when the database is unreachable")
    fun readinessUnavailable() = apiTest(mongoReachable = false) {
        val response = client.get("/ready")

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals(
            ServiceStatus.DOWN,
            json.decodeFromString<ReadinessResponse>(response.bodyAsText()).status,
        )
    }

    @Test
    @DisplayName("GET /version answers without touching the database")
    fun versionEndpoint() = apiTest(mongoReachable = false) {
        val response = client.get("/version")

        assertEquals(HttpStatusCode.OK, response.status)
        val payload = json.decodeFromString<VersionResponse>(response.bodyAsText())
        assertEquals("companero-api", payload.service)
        assertEquals("0.1.0-test", payload.version)
        assertEquals("v1", payload.apiVersion)
        assertEquals("local", payload.environment)
    }

    @Test
    @DisplayName("Responses carry a request id header")
    fun requestIdIsEchoed() = apiTest {
        val response = client.get("/version")

        val requestId = response.headers["X-Request-Id"]
        assertTrue(!requestId.isNullOrBlank(), "X-Request-Id header must be present")
    }

    @Test
    @DisplayName("Unknown routes return the shared error contract")
    fun unknownRoute() = apiTest {
        val response = client.get("/does-not-exist")

        assertEquals(HttpStatusCode.NotFound, response.status)
        val text = response.bodyAsText()
        assertContains(text, "not_found")
        assertContains(text, "requestId")
    }

    @Test
    @DisplayName("Operational endpoints do not disclose configuration")
    fun noConfigurationLeak() = apiTest {
        val bodies = listOf("/health", "/ready", "/version").map { client.get(it).bodyAsText() }

        bodies.forEach { body ->
            assertFalse("mongodb://" in body, body)
            assertFalse("localhost:27017" in body, body)
        }
    }
}
