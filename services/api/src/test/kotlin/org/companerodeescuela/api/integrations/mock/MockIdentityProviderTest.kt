package org.companerodeescuela.api.integrations.mock

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
}
