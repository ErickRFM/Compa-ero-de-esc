package org.companerodeescuela.api.integrations.mock

import org.companerodeescuela.api.integrations.IntegrationException
import org.companerodeescuela.api.integrations.MockIntegrationProvider
import org.companerodeescuela.api.integrations.identity.AuthenticatedAccount
import org.companerodeescuela.api.integrations.identity.IdentityProvider
import org.companerodeescuela.api.integrations.identity.InstitutionalCredentials
import org.companerodeescuela.api.integrations.lms.ExternalAssignment
import org.companerodeescuela.api.integrations.lms.ExternalLearningModule
import org.companerodeescuela.api.integrations.lms.LearningProvider
import org.companerodeescuela.shared.contracts.UserRole

/**
 * Development-only identity provider.
 *
 * It accepts one hard-coded account. The password lives in the fixture because
 * the fixture is development data, but the class refuses to run outside
 * `APP_ENV=local`: [assertDevelopmentOnly] is called by
 * [org.companerodeescuela.api.integrations.ProviderRegistry] when a production
 * registry is built.
 */
class MockIdentityProvider : IdentityProvider, MockIntegrationProvider {
    override val id: String = "mock-identity"
    override val displayName: String = "Mock identity source (development only)"

    override suspend fun authenticate(credentials: InstitutionalCredentials): AuthenticatedAccount? {
        val matches = credentials.username == MOCK_USERNAME &&
            credentials.password == MOCK_PASSWORD
        if (!matches) return null
        return MOCK_ACCOUNT
    }

    override suspend fun refreshRoles(externalId: String): Set<UserRole> {
        if (externalId != MOCK_ACCOUNT.externalId) {
            throw IntegrationException(
                providerId = id,
                category = IntegrationException.Category.NOT_FOUND,
                message = "No account for '$externalId'",
            )
        }
        return MOCK_ACCOUNT.roles
    }

    companion object {
        const val MOCK_USERNAME = "ana.lopez"
        const val MOCK_PASSWORD = "development-only"

        val MOCK_ACCOUNT = AuthenticatedAccount(
            externalId = MockFixtures.STUDENT_ID,
            displayName = "Ana López Hernández",
            email = "ana.lopez@escuela.edu",
            roles = setOf(UserRole.STUDENT),
        )
    }
}

/** Development-only learning management system. */
class MockLearningProvider : LearningProvider, MockIntegrationProvider {
    override val id: String = "mock-learning"
    override val displayName: String = "Mock LMS (development only)"

    override suspend fun listModules(enrollmentId: String): List<ExternalLearningModule> =
        if (enrollmentId in MockFixtures.ENROLLMENTS.map { it.course.externalId }) {
            listOf(
                ExternalLearningModule(
                    externalId = "M-$enrollmentId",
                    title = "Unidad 1 · Repaso",
                    academicCourseId = enrollmentId,
                    openDateIso = "2026-01-12T00:00:00Z",
                ),
            )
        } else {
            emptyList()
        }

    override suspend fun listAssignments(moduleId: String): List<ExternalAssignment> =
        listOf(
            ExternalAssignment(
                externalId = "TASK-$moduleId-1",
                moduleId = moduleId,
                title = "Ejercicio 1",
                dueDateIso = "2026-02-02T23:59:00Z",
                maxScore = 100.0,
            ),
        )
}
