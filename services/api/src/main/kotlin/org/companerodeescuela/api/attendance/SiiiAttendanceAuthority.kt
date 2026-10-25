package org.companerodeescuela.api.attendance

import org.companerodeescuela.api.admin.InstitutionScopePolicy
import org.companerodeescuela.api.auth.PlatformAccountRepository
import org.companerodeescuela.api.auth.SessionIdentitySource
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.integrations.academic.siii.SiiiAcademicService
import org.companerodeescuela.api.integrations.academic.siii.SiiiSectionReader
import org.companerodeescuela.api.integrations.push.InstitutionalIdentityLinkRepository
import org.companerodeescuela.api.integrations.push.InstitutionalIdentityLinkService
import org.companerodeescuela.shared.contracts.AttendanceIdentitySource
import org.companerodeescuela.shared.contracts.AttendanceSessionResponse
import org.companerodeescuela.shared.contracts.ClassOccurrenceContract
import org.companerodeescuela.shared.contracts.ClassOccurrenceStatusContract
import org.companerodeescuela.shared.contracts.InstitutionalAcademicSection
import org.companerodeescuela.shared.contracts.InstitutionalIdentityKind
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary
import java.time.LocalDate

/** Native identities retain live SIII academic authority; no synthetic native groups are created. */
class SiiiAttendanceAuthority(
    private val academic: SiiiAcademicService,
    private val reader: SiiiSectionReader,
    private val links: InstitutionalIdentityLinkRepository,
    private val identities: InstitutionalIdentityLinkService,
    private val accounts: PlatformAccountRepository,
) {
    private val policy = InstitutionScopePolicy(accounts)

    fun configured(actor: UserSummary): Boolean = academic.configured(actor)

    data class Meeting(
        val section: InstitutionalAcademicSection,
        val occurrence: ClassOccurrenceContract,
    )

    suspend fun teacherMeeting(
        actor: UserSummary,
        occurrenceId: String,
        date: LocalDate,
    ): Meeting {
        val load = academic.load(actor, InstitutionalIdentityKind.TEACHER)
        return load.institutionalSections.firstNotNullOfOrNull { section ->
            section.occurrences
                .singleOrNull { it.id == occurrenceId && it.date == date.toString() }
                ?.let { Meeting(section, it) }
        } ?: throw ApiException.NotFound("Current institutional teaching occurrence was not found")
    }

    suspend fun studentGroups(actor: UserSummary): Set<Pair<String, String>> =
        academic
            .load(actor, InstitutionalIdentityKind.STUDENT)
            .institutionalSections
            .map { it.id to it.groupName }
            .toSet()

    suspend fun studentIdsForTeacher(
        actor: UserSummary,
        sectionId: String,
    ): List<String> {
        val current = policy.live(actor)
        val section =
            academic.load(actor, InstitutionalIdentityKind.TEACHER).institutionalSections.singleOrNull { it.id == sectionId }
                ?: throw ApiException.NotFound("Current institutional section was not found")
        val institution = current.institutionId ?: throw ApiException.Forbidden()
        val link =
            links.findByNativeUserId(institution, InstitutionalIdentityKind.TEACHER, current.id)
                ?: throw ApiException.Forbidden("Current institutional teacher link is required")
        val roster =
            reader.teacher(
                current.toUserSummary(),
                SessionIdentitySource.NATIVE,
                institution,
                sectionId,
                link.externalId,
                section.sourceVersion,
            )
        val result = mutableListOf<String>()
        for (enrollment in roster.enrollments.filter { it.status == "enrolled" && it.studentStatus == "active" }) {
            identities.resolve(institution, InstitutionalIdentityKind.STUDENT, enrollment.studentExternalId)?.let { result += it.id }
        }
        val after = academic.load(actor, InstitutionalIdentityKind.TEACHER).institutionalSections.singleOrNull { it.id == sectionId }
        if (after != section) throw ApiException.DependencyUnavailable("Institutional authority changed during the roster read")
        return result.distinct()
    }

    suspend fun requireSession(
        actor: UserSummary,
        session: AttendanceSessionResponse,
    ) {
        val current = policy.live(actor)
        if (session.academicSource != SOURCE ||
            session.identitySource != AttendanceIdentitySource.NATIVE ||
            session.institutionId != current.institutionId ||
            session.groupId != session.courseId ||
            session.groupScopeRevision < 1
        ) {
            throw ApiException.Forbidden("Institutional attendance scope does not match the native account")
        }
        val kind =
            if (current.id == session.openedBy && UserRole.TEACHER in current.roles) {
                InstitutionalIdentityKind.TEACHER
            } else {
                InstitutionalIdentityKind.STUDENT
            }
        val section =
            academic.load(actor, kind).institutionalSections.singleOrNull { it.id == session.courseId }
                ?: throw ApiException.Forbidden("Institutional section is no longer assigned")
        val occurrence =
            section.occurrences.singleOrNull { it.id == session.occurrenceId && it.date == session.occurrenceDate }
                ?: throw ApiException.Forbidden("Institutional occurrence is no longer assigned")
        if (section.sourceVersion != session.groupScopeRevision ||
            section.groupName != session.groupName ||
            occurrence.startsAt != session.scheduledStartsAt ||
            occurrence.endsAt != session.scheduledEndsAt ||
            occurrence.status == ClassOccurrenceStatusContract.CANCELLED
        ) {
            throw ApiException.Forbidden("Institutional attendance scope changed or was revoked")
        }
        val teacher = accounts.findById(session.openedBy) ?: throw ApiException.Forbidden("Assigned teacher is unavailable")
        val assigned =
            try {
                academic.load(teacher.toUserSummary(), InstitutionalIdentityKind.TEACHER)
            } catch (
                _: ApiException.Unauthorized,
            ) {
                throw ApiException.Forbidden("The assigned teacher is no longer active")
            }.institutionalSections
                .singleOrNull { it.id == section.id }
        if (assigned != section) throw ApiException.Forbidden("The opener is no longer assigned to this institutional section")
    }

    companion object {
        const val SOURCE = "SIII"
    }
}
