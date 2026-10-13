package org.companerodeescuela.api.integrations.mock

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.companerodeescuela.api.config.Environment
import org.companerodeescuela.api.integrations.identity.InstitutionalCredentials
import org.companerodeescuela.shared.contracts.UserRole

class MockIdentityProviderTest {
    private val provider = MockIdentityProvider()

    @Test
    fun qaIdentitiesAuthenticateWithExpectedRoles() = runTest {
        val student = provider.authenticate(
            InstitutionalCredentials(MockIdentityProvider.QA_STUDENT_USERNAME, MockIdentityProvider.QA_PASSWORD),
        )
        val teacher = provider.authenticate(
            InstitutionalCredentials(MockIdentityProvider.QA_TEACHER_USERNAME, MockIdentityProvider.QA_PASSWORD),
        )
        val supervisor = provider.authenticate(
            InstitutionalCredentials(MockIdentityProvider.QA_SUPERVISOR_USERNAME, MockIdentityProvider.QA_PASSWORD),
        )
        val admin = provider.authenticate(
            InstitutionalCredentials(MockIdentityProvider.QA_ADMIN_USERNAME, MockIdentityProvider.QA_PASSWORD),
        )

        assertEquals(setOf(UserRole.STUDENT), assertNotNull(student).roles)
        assertEquals(setOf(UserRole.TEACHER), assertNotNull(teacher).roles)
        assertEquals(setOf(UserRole.COORDINATOR), assertNotNull(supervisor).roles)
        assertEquals(setOf(UserRole.ADMIN), assertNotNull(admin).roles)
    }

    @Test
    fun qaPasswordDoesNotAuthenticateDemoIdentity() = runTest {
        val account = provider.authenticate(
            InstitutionalCredentials(MockIdentityProvider.MOCK_USERNAME, MockIdentityProvider.QA_PASSWORD),
        )
        assertEquals(null, account)
    }

    @Test
    fun demoAndQaCredentialsAuthenticateOnlyInLocal() = runTest {
        val constructor = MockIdentityProvider::class.java.declaredConstructors.firstOrNull {
            it.parameterTypes.contentEquals(arrayOf(Environment::class.java))
        }
        assertNotNull(constructor, "mock identity must receive the active environment")
        val credentials = listOf(
            InstitutionalCredentials(MockIdentityProvider.MOCK_USERNAME, MockIdentityProvider.MOCK_PASSWORD),
            InstitutionalCredentials(MockIdentityProvider.MOCK_TEACHER_USERNAME, MockIdentityProvider.MOCK_TEACHER_PASSWORD),
            InstitutionalCredentials(MockIdentityProvider.QA_STUDENT_USERNAME, MockIdentityProvider.QA_PASSWORD),
            InstitutionalCredentials(MockIdentityProvider.QA_TEACHER_USERNAME, MockIdentityProvider.QA_PASSWORD),
            InstitutionalCredentials(MockIdentityProvider.QA_SUPERVISOR_USERNAME, MockIdentityProvider.QA_PASSWORD),
            InstitutionalCredentials(MockIdentityProvider.QA_ADMIN_USERNAME, MockIdentityProvider.QA_PASSWORD),
        )

        credentials.forEach { credential ->
            assertNotNull(constructor.newInstance(Environment.LOCAL).let { (it as MockIdentityProvider).authenticate(credential) })
            listOf(Environment.DEVELOPMENT, Environment.STAGING, Environment.PRODUCTION).forEach { environment ->
                assertNull((constructor.newInstance(environment) as MockIdentityProvider).authenticate(credential))
            }
        }
    }

}
