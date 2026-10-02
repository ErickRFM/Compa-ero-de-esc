package org.companerodeescuela.api.health

import java.time.Clock
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.companerodeescuela.api.test.FakeMongoConnection
import org.companerodeescuela.api.test.TestFixtures
import org.companerodeescuela.shared.contracts.ServiceStatus
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class HealthServiceTest {

    @Test
    @DisplayName("Health is UP when no database is configured")
    fun healthWithoutDatabase() = runTest {
        val service = HealthService(
            settings = TestFixtures.settings(),
            mongoConnection = FakeMongoConnection(reachable = false),
            clock = TestFixtures.FIXED_CLOCK,
        )

        val health = service.health()

        assertEquals(ServiceStatus.UP, health.status)
        assertEquals("companero-api", health.service)
        assertEquals("0.1.0-test", health.version)
        assertEquals("local", health.environment)
        assertEquals(TestFixtures.FIXED_INSTANT, health.timestamp)

        val dependency = health.dependencies.single()
        assertEquals(HealthService.MONGODB_DEPENDENCY, dependency.name)
        assertEquals(ServiceStatus.DEGRADED, dependency.status)
        assertEquals("not_configured", dependency.detail)
    }

    @Test
    @DisplayName("Health is UP when the database answers")
    fun healthWithReachableDatabase() = runTest {
        val service = HealthService(
            settings = TestFixtures.settings(mongoUri = "mongodb://localhost:27017"),
            mongoConnection = FakeMongoConnection(reachable = true),
            clock = TestFixtures.FIXED_CLOCK,
        )

        val health = service.health()

        assertEquals(ServiceStatus.UP, health.status)
        assertEquals(ServiceStatus.UP, health.dependencies.single().status)
    }

    @Test
    @DisplayName("Health is DEGRADED, not DOWN, when the database is unreachable")
    fun healthDegradesInsteadOfFailing() = runTest {
        val service = HealthService(
            settings = TestFixtures.settings(mongoUri = "mongodb://localhost:27017"),
            mongoConnection = FakeMongoConnection(reachable = false),
            clock = TestFixtures.FIXED_CLOCK,
        )

        val health = service.health()

        assertEquals(ServiceStatus.DEGRADED, health.status)
        val dependency = health.dependencies.single()
        assertEquals(ServiceStatus.DOWN, dependency.status)
        assertEquals("unreachable", dependency.detail)
    }

    @Test
    @DisplayName("Readiness is DOWN when the database is not configured")
    fun readinessRequiresDatabase() = runTest {
        val service = HealthService(
            settings = TestFixtures.settings(),
            mongoConnection = FakeMongoConnection(reachable = false),
            clock = TestFixtures.FIXED_CLOCK,
        )

        val readiness = service.readiness()

        assertEquals(ServiceStatus.DOWN, readiness.status)
        assertEquals(1, readiness.checks.size)
    }

    @Test
    @DisplayName("Readiness is UP when the database answers")
    fun readinessWithDatabase() = runTest {
        val service = HealthService(
            settings = TestFixtures.settings(mongoUri = "mongodb://localhost:27017"),
            mongoConnection = FakeMongoConnection(reachable = true),
            clock = TestFixtures.FIXED_CLOCK,
        )

        val readiness = service.readiness()

        assertEquals(ServiceStatus.UP, readiness.status)
        assertEquals(TestFixtures.FIXED_INSTANT, readiness.timestamp)
    }

    @Test
    @DisplayName("Health never leaks the connection string")
    fun healthDoesNotLeakConnectionString() = runTest {
        val uri = "mongodb+srv://user:hunter2@cluster.example.edu/companero"
        val service = HealthService(
            settings = TestFixtures.settings(mongoUri = uri),
            mongoConnection = FakeMongoConnection(reachable = false),
            clock = TestFixtures.FIXED_CLOCK,
        )

        val health = service.health()
        val rendered = health.toString()

        assertTrue("hunter2" !in rendered, rendered)
        assertTrue("cluster.example.edu" !in rendered, rendered)
    }
}
