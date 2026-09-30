package org.companerodeescuela.api.application

import io.ktor.client.request.get
import io.ktor.server.testing.testApplication
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.companerodeescuela.api.config.Environment
import org.companerodeescuela.api.integrations.ProviderRegistry
import org.companerodeescuela.api.test.FakeMongoConnection
import org.companerodeescuela.api.test.TestFixtures
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Boots the real application module in every environment.
 *
 * This suite exists because BUG-001 shipped a correct, fully tested
 * `requireEnvironmentSatisfied` that was never called from the boot path. The
 * unit tests in ProviderRegistryTest all passed, and production would still
 * have served fabricated timetables. A test of a pure function verifies the
 * rule; only this test verifies the enforcement.
 */
class ApplicationModuleBootTest {

    @Test
    @DisplayName("The application module refuses to boot in production with mock providers")
    fun productionBootRefusesMocks() {
        val error = assertFailsWith<IllegalStateException> {
            testApplication {
                application {
                    module(
                        settings = TestFixtures.settings(
                            environment = Environment.PRODUCTION,
                            mongoUri = "mongodb://localhost:27017",
                        ),
                        mongoConnection = FakeMongoConnection(reachable = true),
                    )
                }
            }
        }

        assertContains(error.message!!, "PRODUCTION")
        assertContains(error.message!!, "mock-academic")
        assertContains(error.message!!, "mock-identity")
    }

    @Test
    @DisplayName("The application module refuses to boot in staging with mock providers")
    fun stagingBootRefusesMocks() {
        assertFailsWith<IllegalStateException> {
            testApplication {
                application {
                    module(
                        settings = TestFixtures.settings(
                            environment = Environment.STAGING,
                            mongoUri = "mongodb://localhost:27017",
                        ),
                        mongoConnection = FakeMongoConnection(reachable = true),
                    )
                }
            }
        }
    }

    @Test
    @DisplayName("The application module boots in local with mock providers")
    fun localBootAcceptsMocks() = testApplication {
        application {
            module(
                settings = TestFixtures.settings(
                    environment = Environment.LOCAL,
                    mongoUri = "mongodb://localhost:27017",
                ),
                mongoConnection = FakeMongoConnection(reachable = true),
            )
        }

        val response = client.get("/health")
        assertTrue(response.status.value == 200, "local must serve health with mocks")
    }

    @Test
    @DisplayName("The guard runs before any route is reachable")
    fun guardRunsBeforeRouting() {
        // If the check were moved below configurePlugins or into a route, a
        // partially installed application could still answer a request before
        // the failure. Booting must fail with nothing served.
        assertFailsWith<IllegalStateException> {
            testApplication {
                application {
                    module(
                        settings = TestFixtures.settings(
                            environment = Environment.PRODUCTION,
                            mongoUri = "mongodb://localhost:27017",
                        ),
                        mongoConnection = FakeMongoConnection(reachable = true),
                        providerRegistry = ProviderRegistry.mocks(),
                    )
                }
                // Unreachable: the module throws before this client call runs.
                client.get("/health")
            }
        }
    }
}
