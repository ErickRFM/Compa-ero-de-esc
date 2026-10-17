package org.companerodeescuela.api.integrations.academic.siii

import kotlinx.serialization.Serializable
import org.companerodeescuela.api.admin.InstitutionScopePolicy
import org.companerodeescuela.api.auth.PlatformAccountRepository
import org.companerodeescuela.api.auth.SessionIdentitySource
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.institutions.InstitutionRepository
import org.companerodeescuela.api.integrations.push.InstitutionalIdentityLinkService
import org.companerodeescuela.shared.contracts.InstitutionalIdentityKind
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary
import java.time.Clock

@Serializable
data class SiiiSectionDetails(
    val sectionId: String,
    val sectionCode: String,
    val status: String,
    val timezone: String,
    val term: SiiiTerm,
    val subject: SiiiSubject,
    val teachers: List<SiiiTeacher>,
    val occurrences: List<SiiiOccurrence>,
    val sourceVersion: Long,
    val syncedAt: String,
)

@Serializable
data class SiiiStudentSectionView(
    val section: SiiiSectionDetails,
    val enrollment: SiiiEnrollment,
)

@Serializable
data class SiiiTeacherSectionView(
    val section: SiiiSectionDetails,
    val enrollments: List<SiiiEnrollment>,
)

/**
 * Internal service. HTTP callers must obtain actor from requireNativeActor and identitySource
 * from verifiedSessionSource after the existing session authority has validated the request.
 * UserSummary and a matching account ID alone do not prove a native or non-revoked session.
 * No HTTP/mobile route is exposed by this slice.
 */
class SiiiSectionReader(
    private val accounts: PlatformAccountRepository,
    private val institutions: InstitutionRepository,
    private val identities: InstitutionalIdentityLinkService,
    private val repository: SiiiSectionSnapshotRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val policy = InstitutionScopePolicy(accounts, clock)

    suspend fun student(
        actor: UserSummary,
        identitySource: SessionIdentitySource,
        institutionId: String,
        sectionId: String,
        externalId: String,
    ): SiiiStudentSectionView {
        authorize(actor, identitySource, institutionId, externalId, InstitutionalIdentityKind.STUDENT, UserRole.STUDENT)
        val stored = available(institutionId, sectionId)
        val snapshot = checkNotNull(stored.snapshot)
        val own = snapshot.enrollments.singleOrNull { it.studentExternalId == externalId && it.status == "enrolled" }
        if (snapshot.status != "open" || own == null) throw ApiException.Forbidden()
        return SiiiStudentSectionView(stored.details(), own)
    }

    suspend fun teacher(
        actor: UserSummary,
        identitySource: SessionIdentitySource,
        institutionId: String,
        sectionId: String,
        externalId: String,
    ): SiiiTeacherSectionView {
        authorize(actor, identitySource, institutionId, externalId, InstitutionalIdentityKind.TEACHER, UserRole.TEACHER)
        val stored = available(institutionId, sectionId)
        val snapshot = checkNotNull(stored.snapshot)
        if (snapshot.teachers.none { it.externalId == externalId }) throw ApiException.Forbidden()
        return SiiiTeacherSectionView(stored.details(), snapshot.enrollments)
    }

    private suspend fun authorize(
        actor: UserSummary,
        identitySource: SessionIdentitySource,
        institutionId: String,
        externalId: String,
        kind: InstitutionalIdentityKind,
        role: UserRole,
    ) {
        if (identitySource != SessionIdentitySource.NATIVE) throw ApiException.Forbidden("A native platform session is required")
        val current = policy.live(actor)
        if (current.institutionId != institutionId ||
            role !in current.roles ||
            institutions.findEnabled(institutionId) == null
        ) {
            throw ApiException.Forbidden()
        }
        val linked = identities.resolve(institutionId, kind, externalId)
        if (linked == null || linked.id != current.id || linked.authRevision != current.authRevision) throw ApiException.Forbidden()
    }

    private suspend fun available(
        institutionId: String,
        sectionId: String,
    ): StoredSiiiSection {
        val stored = repository.find(institutionId, sectionId) ?: throw ApiException.NotFound()
        if (stored.snapshot == null || stored.snapshot.status == "archived") throw ApiException.NotFound()
        return stored
    }

    private fun StoredSiiiSection.details(): SiiiSectionDetails {
        val value = checkNotNull(snapshot)
        return SiiiSectionDetails(
            value.sectionId,
            value.sectionCode,
            value.status,
            value.timezone,
            value.term,
            value.subject,
            value.teachers,
            value.occurrences,
            sourceVersion,
            syncedAt.toString(),
        )
    }
}
