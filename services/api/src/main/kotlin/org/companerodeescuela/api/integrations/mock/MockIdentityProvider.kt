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
 * Demo identities and clean QA identities are intentionally explicit. The
 * provider itself is rejected outside local development by ProviderRegistry,
 * so QA credentials can never become a production authentication path.
 */
class MockIdentityProvider : IdentityProvider, MockIntegrationProvider {
    override val id: String = "mock-identity"
    override val displayName: String = "Mock identity source (development only)"

    override suspend fun authenticate(credentials: InstitutionalCredentials): AuthenticatedAccount? =
        accountFor(credentials.username, credentials.password)

    override suspend fun refreshRoles(externalId: String): Set<UserRole> =
        ALL_ACCOUNTS.firstOrNull { it.externalId == externalId }?.roles
            ?: throw IntegrationException(
                providerId = id,
                category = IntegrationException.Category.NOT_FOUND,
                message = "No account for '$externalId'",
            )

    private fun accountFor(username: String, password: String): AuthenticatedAccount? = when {
        username == MOCK_USERNAME && password == MOCK_PASSWORD -> MOCK_ACCOUNT
        username == MOCK_TEACHER_USERNAME && password == MOCK_TEACHER_PASSWORD -> MOCK_TEACHER_ACCOUNT
        username == QA_STUDENT_USERNAME && password == QA_PASSWORD -> QA_STUDENT_ACCOUNT
        username == QA_TEACHER_USERNAME && password == QA_PASSWORD -> QA_TEACHER_ACCOUNT
        username == QA_SUPERVISOR_USERNAME && password == QA_PASSWORD -> QA_SUPERVISOR_ACCOUNT
        username == QA_ADMIN_USERNAME && password == QA_PASSWORD -> QA_ADMIN_ACCOUNT
        else -> null
    }

    companion object {
        const val MOCK_USERNAME = "ana.lopez"
        const val MOCK_PASSWORD = "development-only"
        const val MOCK_TEACHER_USERNAME = "elena.rios"
        const val MOCK_TEACHER_PASSWORD = "development-only-teacher"

        const val QA_STUDENT_USERNAME = "qa.alumno"
        const val QA_TEACHER_USERNAME = "qa.docente"
        const val QA_SUPERVISOR_USERNAME = "qa.supervisor"
        const val QA_ADMIN_USERNAME = "qa.admin"
        const val QA_PASSWORD = "qa-development-only"

        val MOCK_ACCOUNT = AuthenticatedAccount(
            externalId = MockFixtures.STUDENT_ID,
            displayName = "Ana López Hernández",
            email = "ana.lopez@escuela.edu",
            roles = setOf(UserRole.STUDENT),
        )

        val MOCK_TEACHER_ACCOUNT = AuthenticatedAccount(
            externalId = MockFixtures.TEACHER_ID,
            displayName = "Mtra. Elena Ríos Salgado",
            email = "elena.rios@escuela.edu",
            roles = setOf(UserRole.TEACHER),
        )

        val QA_STUDENT_ACCOUNT = AuthenticatedAccount(
            externalId = MockFixtures.QA_STUDENT_ID,
            displayName = "QA Alumno",
            email = "qa.alumno@example.invalid",
            roles = setOf(UserRole.STUDENT),
        )

        val QA_TEACHER_ACCOUNT = AuthenticatedAccount(
            externalId = MockFixtures.QA_TEACHER_ID,
            displayName = "QA Docente",
            email = "qa.docente@example.invalid",
            roles = setOf(UserRole.TEACHER),
        )

        val QA_SUPERVISOR_ACCOUNT = AuthenticatedAccount(
            externalId = MockFixtures.QA_SUPERVISOR_ID,
            displayName = "QA Supervisor",
            email = "qa.supervisor@example.invalid",
            roles = setOf(UserRole.COORDINATOR),
        )

        val QA_ADMIN_ACCOUNT = AuthenticatedAccount(
            externalId = MockFixtures.QA_ADMIN_ID,
            displayName = "QA Admin",
            email = "qa.admin@example.invalid",
            roles = setOf(UserRole.ADMIN),
        )

        val ALL_ACCOUNTS = listOf(
            MOCK_ACCOUNT,
            MOCK_TEACHER_ACCOUNT,
            QA_STUDENT_ACCOUNT,
            QA_TEACHER_ACCOUNT,
            QA_SUPERVISOR_ACCOUNT,
            QA_ADMIN_ACCOUNT,
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
