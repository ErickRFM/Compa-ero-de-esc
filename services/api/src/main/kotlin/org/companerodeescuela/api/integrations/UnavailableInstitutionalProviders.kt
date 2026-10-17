package org.companerodeescuela.api.integrations

import org.companerodeescuela.api.integrations.academic.AcademicProvider
import org.companerodeescuela.api.integrations.academic.dto.ExternalAcademicLoad
import org.companerodeescuela.api.integrations.academic.dto.ExternalCourse
import org.companerodeescuela.api.integrations.academic.dto.ExternalScheduleSlot
import org.companerodeescuela.api.integrations.academic.dto.ExternalStudent
import org.companerodeescuela.api.integrations.academic.dto.ExternalTeacher
import org.companerodeescuela.api.integrations.events.EventsProvider
import org.companerodeescuela.api.integrations.events.ExternalEvent
import org.companerodeescuela.api.integrations.identity.AuthenticatedAccount
import org.companerodeescuela.api.integrations.identity.IdentityProvider
import org.companerodeescuela.api.integrations.identity.InstitutionalCredentials
import org.companerodeescuela.api.integrations.identity.InstitutionalIdentityState
import org.companerodeescuela.api.integrations.library.ExternalLibraryItem
import org.companerodeescuela.api.integrations.library.ExternalLoan
import org.companerodeescuela.api.integrations.library.LibraryProvider
import org.companerodeescuela.api.integrations.lms.ExternalAssignment
import org.companerodeescuela.api.integrations.lms.ExternalLearningModule
import org.companerodeescuela.api.integrations.lms.LearningProvider
import org.companerodeescuela.api.integrations.sync.SyncOutcome
import org.companerodeescuela.api.integrations.sync.SyncProvider
import org.companerodeescuela.shared.contracts.UserRole
import java.time.Instant
import java.time.LocalDate

private fun unavailable(providerId: String): Nothing =
    throw IntegrationException(providerId, IntegrationException.Category.UNAVAILABLE, "Institutional integration is not configured")

/** Explicit absence of an institutional source; these providers never fabricate data. */
object UnavailableAcademicProvider : AcademicProvider {
    override val id = "unavailable-academic"
    override val displayName = "Academic integration not configured"

    override suspend fun getStudent(externalId: String): ExternalStudent? = unavailable(id)

    override suspend fun getAcademicLoad(externalId: String): ExternalAcademicLoad = unavailable(id)

    override suspend fun listCourses(): List<ExternalCourse> = unavailable(id)

    override suspend fun getSchedule(
        externalId: String,
        weekOf: LocalDate?,
    ): List<ExternalScheduleSlot> = unavailable(id)

    override suspend fun listTeachers(): List<ExternalTeacher> = unavailable(id)
}

/** Native Mongo authentication remains separate; no upstream identity can be verified here. */
object UnavailableIdentityProvider : IdentityProvider {
    override val id = "unavailable-identity"
    override val displayName = "Institutional identity not configured"

    override suspend fun authenticate(credentials: InstitutionalCredentials): AuthenticatedAccount? = null

    override suspend fun refreshRoles(externalId: String): Set<UserRole> = unavailable(id)

    override suspend fun currentState(externalId: String): InstitutionalIdentityState? = null
}

object UnavailableLearningProvider : LearningProvider {
    override val id = "unavailable-learning"
    override val displayName = "Learning integration not configured"

    override suspend fun listModules(enrollmentId: String): List<ExternalLearningModule> = unavailable(id)

    override suspend fun listAssignments(moduleId: String): List<ExternalAssignment> = unavailable(id)
}

object UnavailableLibraryProvider : LibraryProvider {
    override val id = "unavailable-library"
    override val displayName = "Library integration not configured"

    override suspend fun search(
        query: String,
        limit: Int,
    ): List<ExternalLibraryItem> = unavailable(id)

    override suspend fun listLoans(borrowerExternalId: String): List<ExternalLoan> = unavailable(id)
}

object UnavailableEventsProvider : EventsProvider {
    override val id = "unavailable-events"
    override val displayName = "Institutional events not configured"

    override suspend fun listEvents(
        from: LocalDate,
        to: LocalDate,
    ): List<ExternalEvent> = unavailable(id)
}

object UnavailableSyncProvider : SyncProvider {
    override val id = "unavailable-sync"
    override val displayName = "Institutional sync not configured"
    override val dataset = "academic-load"

    override suspend fun sync(
        externalId: String,
        since: Instant?,
    ): SyncOutcome = unavailable(id)
}
