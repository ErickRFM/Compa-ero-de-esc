package org.companerodeescuela.api.health

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.companerodeescuela.api.application.module
import org.companerodeescuela.api.config.MongoSettings
import org.companerodeescuela.api.database.DriverMongoConnectionFactory
import org.companerodeescuela.api.test.TestFixtures
import org.companerodeescuela.shared.contracts.HealthResponse
import org.companerodeescuela.shared.contracts.ReadinessResponse
import org.companerodeescuela.shared.contracts.ServiceStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

@Timeout(value = 15, unit = TimeUnit.SECONDS)
class MongoHealthRoutesTest {
    @Test
    fun `a driver ping timeout keeps liveness available and degraded`() =
        unavailableApi {
            val response = client.get("/health")
            assertEquals(HttpStatusCode.OK, response.status)
            val body = Json.decodeFromString<HealthResponse>(response.bodyAsText())
            assertEquals(ServiceStatus.DEGRADED, body.status)
            assertEquals(ServiceStatus.DOWN, body.dependencies.single().status)
            assertEquals("unreachable", body.dependencies.single().detail)
        }

    @Test
    fun `a driver ping timeout makes readiness unavailable instead of an internal error`() =
        unavailableApi {
            val response = client.get("/ready")
            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            val body = Json.decodeFromString<ReadinessResponse>(response.bodyAsText())
            assertEquals(ServiceStatus.DOWN, body.status)
            assertEquals("unreachable", body.checks.single().detail)
        }

    private fun unavailableApi(block: suspend ApplicationTestBuilder.() -> Unit) {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { socket ->
            val mongo = MongoSettings("mongodb://127.0.0.1:${socket.localPort}/?serverSelectionTimeoutMS=5000", "readiness_test")
            DriverMongoConnectionFactory(pingTimeoutMs = 100).create(mongo).use { connection ->
                connection.database()
                testApplication {
                    application { module(settings = TestFixtures.settings().copy(mongo = mongo), mongoConnection = connection) }
                    block()
                }
            }
        }
    }
}
