package org.companerodeescuela.api.integrations

import kotlinx.coroutines.runBlocking
import org.companerodeescuela.api.config.Environment
import org.companerodeescuela.api.integrations.identity.InstitutionalCredentials
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NativeOnlyProvidersTest {
    @Test
    fun `environment defaults keep development fixtures and never configure mocks in staging or production`() {
        for (environment in Environment.entries) {
            val registry = ProviderRegistry.defaultsFor(environment)
            if (environment in setOf(Environment.LOCAL, Environment.DEVELOPMENT)) {
                assertEquals(6, registry.mockProviders().size)
            } else {
                assertTrue(registry.mockProviders().isEmpty())
                assertEquals(6, registry.all().size)
                assertTrue(registry.all().all { it.id.startsWith("unavailable-") })
                ProviderRegistry.requireEnvironmentSatisfied(registry, environment)
                assertFailsWith<IllegalStateException> {
                    ProviderRegistry.requireEnvironmentSatisfied(
                        ProviderRegistry.mocks(),
                        environment,
                    )
                }
            }
        }
    }

    @Test
    fun `every unconfigured institutional read and sync fails explicitly without empty successful data`() =
        runBlocking {
            val registry = ProviderRegistry.nativeOnly()
            val operations: List<suspend () -> Any?> =
                listOf(
                    { registry.academic.getStudent("fixture-student") },
                    { registry.academic.getAcademicLoad("fixture-student") },
                    { registry.academic.listCourses() },
                    { registry.academic.getSchedule("fixture-student", LocalDate.of(2026, 10, 17)) },
                    { registry.academic.listTeachers() },
                    { registry.identity.refreshRoles("fixture-identity") },
                    { registry.learning.listModules("fixture-enrollment") },
                    { registry.learning.listAssignments("fixture-module") },
                    { registry.library.search("fixture-query") },
                    { registry.library.listLoans("fixture-borrower") },
                    { registry.events.listEvents(LocalDate.of(2026, 10, 17), LocalDate.of(2026, 10, 18)) },
                    { registry.sync.single().sync("fixture-identity", null) },
                )
            for (operation in operations) {
                val failure = assertFailsWith<IntegrationException> { operation() }
                assertEquals(IntegrationException.Category.UNAVAILABLE, failure.category)
                assertTrue(failure.providerId.startsWith("unavailable-"))
                assertEquals("${failure.providerId}/UNAVAILABLE: Institutional integration is not configured", failure.message)
            }
            assertNull(registry.identity.authenticate(InstitutionalCredentials("fixture-unknown", "fixture-password")))
            assertNull(registry.identity.currentState("fixture-identity"))
        }
}
