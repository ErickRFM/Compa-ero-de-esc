package org.companerodeescuela.api.integrations

import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.companerodeescuela.api.config.Environment
import org.companerodeescuela.api.integrations.identity.InstitutionalCredentials
import org.companerodeescuela.api.integrations.mock.MockAcademicProvider
import org.companerodeescuela.api.integrations.mock.MockFixtures
import org.companerodeescuela.api.integrations.mock.MockIdentityProvider
import org.companerodeescuela.shared.contracts.UserRole
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class MockProviderTest {

    @Test
    @DisplayName("Mock academic provider serves fixture data for a known student")
    fun servesFixtures() = runTest {
        val provider = MockAcademicProvider()

        val load = provider.getAcademicLoad(MockFixtures.STUDENT_ID)

        assertEquals(2, load.enrollments.size)
        assertEquals(3, load.schedule.size)
        assertEquals("Ana López Hernández", load.student.fullName)
    }

    @Test
    @DisplayName("Mock academic provider returns null for an unknown student lookup")
    fun unknownStudentIsNullOnLookup() = runTest {
        assertNull(MockAcademicProvider().getStudent("does-not-exist"))
    }

    @Test
    @DisplayName("Mock academic provider throws NOT_FOUND for an unknown academic load")
    fun unknownStudentThrowsOnLoad() = runTest {
        val error = assertFailsWith<IntegrationException> {
            MockAcademicProvider().getAcademicLoad("does-not-exist")
        }

        assertEquals(IntegrationException.Category.NOT_FOUND, error.category)
    }

    @Test
    @DisplayName("Mock identity provider accepts only its documented account")
    fun identityAcceptsDocumentedAccount() = runTest {
        val provider = MockIdentityProvider()

        val account = provider.authenticate(
            InstitutionalCredentials(
                username = MockIdentityProvider.MOCK_USERNAME,
                password = MockIdentityProvider.MOCK_PASSWORD,
            ),
        )

        assertNotNull(account)
        assertEquals(setOf(UserRole.STUDENT), account.roles)
    }

    @Test
    @DisplayName("Mock identity provider returns null for a wrong password")
    fun identityRejectsWrongPassword() = runTest {
        val account = MockIdentityProvider().authenticate(
            InstitutionalCredentials(MockIdentityProvider.MOCK_USERNAME, "wrong"),
        )

        assertNull(account, "authentication must not distinguish unknown user from wrong password")
    }

    @Test
    @DisplayName("Mock identity provider returns null for an unknown user")
    fun identityRejectsUnknownUser() = runTest {
        val account = MockIdentityProvider().authenticate(
            InstitutionalCredentials("nobody", MockIdentityProvider.MOCK_PASSWORD),
        )

        assertNull(account)
    }
}

class ProviderRegistryTest {

    @Test
    @DisplayName("The default registry is fully mocked so a fresh clone boots")
    fun defaultRegistryIsMocked() {
        val registry = ProviderRegistry.mocks()

        assertEquals(5 + registry.sync.size, registry.mockProviders().size)
        assertTrue(registry.all().isNotEmpty())
    }

    @Test
    @DisplayName("Local development accepts mock providers")
    fun localAcceptsMocks() {
        ProviderRegistry.requireEnvironmentSatisfied(ProviderRegistry.mocks(), Environment.LOCAL)
        ProviderRegistry.requireEnvironmentSatisfied(
            ProviderRegistry.mocks(),
            Environment.DEVELOPMENT,
        )
    }

    @Test
    @DisplayName("Staging and production refuse to boot with mock providers")
    fun productionRefusesMocks() {
        listOf(Environment.STAGING, Environment.PRODUCTION).forEach { environment ->
            val error = assertFailsWith<IllegalStateException> {
                ProviderRegistry.requireEnvironmentSatisfied(ProviderRegistry.mocks(), environment)
            }
            assertContains(error.message!!, "mock-academic")
        }
    }
}
