package org.companerodeescuela.api.integrations.academic.siii

import kotlinx.serialization.Serializable
import org.companerodeescuela.api.admin.InstitutionScopePolicy
import org.companerodeescuela.api.auth.PlatformAccountRepository
import org.companerodeescuela.api.auth.SessionIdentitySource
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.integrations.push.InstitutionalIdentityLinkRepository
import org.companerodeescuela.shared.contracts.AcademicLoadResponse
import org.companerodeescuela.shared.contracts.AcademicProfile
import org.companerodeescuela.shared.contracts.AcademicScheduleResponse
import org.companerodeescuela.shared.contracts.ClassOccurrenceContract
import org.companerodeescuela.shared.contracts.ClassOccurrenceStatusContract
import org.companerodeescuela.shared.contracts.InstitutionalAcademicSection
import org.companerodeescuela.shared.contracts.InstitutionalIdentityKind
import org.companerodeescuela.shared.contracts.ScheduleEntry
import org.companerodeescuela.shared.contracts.ScheduleRecurrence
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Serializable
data class SiiiAuthoritySection(
    val sectionId: String,
    val sourceVersion: Long,
)

@Serializable
data class SiiiAuthorityManifest(
    val contract: String,
    val institutionId: String,
    val kind: String,
    val externalId: String,
    val verifiedAt: String,
    val sections: List<SiiiAuthoritySection>,
)

fun interface SiiiAcademicAuthority {
    suspend fun current(
        institutionId: String,
        kind: InstitutionalIdentityKind,
        externalId: String,
    ): SiiiAuthorityManifest
}

/** Joins live source authority to exact durable snapshots through the existing reader. */
class SiiiAcademicService(
    accounts: PlatformAccountRepository,
    private val links: InstitutionalIdentityLinkRepository,
    private val reader: SiiiSectionReader,
    private val authority: SiiiAcademicAuthority,
    private val configuredInstitutions: Set<String>,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val policy = InstitutionScopePolicy(accounts, clock)

    fun configured(actor: UserSummary): Boolean = actor.institutionId in configuredInstitutions

    suspend fun load(
        actor: UserSummary,
        kind: InstitutionalIdentityKind? = null,
    ): AcademicLoadResponse {
        val current = policy.live(actor)
        val institution = current.institutionId ?: throw ApiException.Forbidden("Institutional membership is required")
        if (institution !in
            configuredInstitutions
        ) {
            throw ApiException.DependencyUnavailable("Institutional academic delivery is not configured")
        }
        val granted =
            InstitutionalIdentityKind.entries.filter {
                (
                    if (it ==
                        InstitutionalIdentityKind.STUDENT
                    ) {
                        UserRole.STUDENT
                    } else {
                        UserRole.TEACHER
                    }
                ) in
                    current.roles
            }
        if (granted.isEmpty() ||
            (kind != null && kind !in granted)
        ) {
            throw ApiException.Forbidden("An institutional student or teacher role is required")
        }
        val identities =
            (kind?.let(::listOf) ?: granted).mapNotNull { candidate ->
                links.findByNativeUserId(institution, candidate, current.id)?.let {
                    candidate to
                        it
                }
            }
        if (identities.isEmpty()) throw ApiException.NotFound("Institutional identity is not linked")
        val loads = identities.map { (candidate, _) -> loadForKind(actor, candidate) }
        val sections =
            loads
                .flatMap { it.institutionalSections }
                .groupBy { it.id }
                .map { (_, copies) ->
                    if (copies.distinct().size !=
                        1
                    ) {
                        throw ApiException.DependencyUnavailable("Institutional authority changed during the read")
                    }
                    copies.first()
                }.sortedBy { it.id }
        identities.zip(loads).forEach { (identity, load) ->
            val (candidate, link) = identity
            val expected = load.institutionalSections.map { SiiiAuthoritySection(it.id, it.sourceVersion) }.sortedBy { it.sectionId }
            if (manifest(institution, candidate, link.externalId).sections.sortedBy { it.sectionId } != expected ||
                links.findByNativeUserId(institution, candidate, current.id) != link
            ) {
                throw ApiException.DependencyUnavailable("Institutional authority changed during the read")
            }
        }
        policy.live(actor)
        return loads.first().copy(
            institutionalSections = sections,
            schedule =
                AcademicScheduleResponse(
                    current.id,
                    loads
                        .flatMap {
                            it.schedule.entries
                        }.distinct(),
                ),
        )
    }

    private suspend fun loadForKind(
        actor: UserSummary,
        kind: InstitutionalIdentityKind,
    ): AcademicLoadResponse {
        val current = policy.live(actor)
        val institutionId = current.institutionId ?: throw ApiException.Forbidden("Institutional membership is required")
        if (institutionId !in
            configuredInstitutions
        ) {
            throw ApiException.DependencyUnavailable("Institutional academic delivery is not configured")
        }
        if ((if (kind == InstitutionalIdentityKind.STUDENT) UserRole.STUDENT else UserRole.TEACHER) !in
            current.roles
        ) {
            throw ApiException.Forbidden()
        }
        val link =
            links.findByNativeUserId(institutionId, kind, current.id)
                ?: throw ApiException.NotFound("Institutional identity is not linked")
        reader.verifyIdentity(current.toUserSummary(), institutionId, link.externalId, kind)
        val manifest = manifest(institutionId, kind, link.externalId)
        val sections =
            manifest.sections.map { expected ->
                val details =
                    try {
                        when (kind) {
                            InstitutionalIdentityKind.STUDENT ->
                                reader
                                    .student(
                                        current.toUserSummary(),
                                        SessionIdentitySource.NATIVE,
                                        institutionId,
                                        expected.sectionId,
                                        link.externalId,
                                        expected.sourceVersion,
                                    ).section
                            InstitutionalIdentityKind.TEACHER ->
                                reader
                                    .teacher(
                                        current.toUserSummary(),
                                        SessionIdentitySource.NATIVE,
                                        institutionId,
                                        expected.sectionId,
                                        link.externalId,
                                        expected.sourceVersion,
                                    ).section
                        }
                    } catch (
                        _: ApiException.NotFound,
                    ) {
                        throw ApiException.DependencyUnavailable("Institutional snapshot is pending synchronization")
                    }
                if (details.sourceVersion !=
                    expected.sourceVersion
                ) {
                    throw ApiException.DependencyUnavailable("Institutional snapshot is pending synchronization")
                }
                details.toContract()
            }
        // A source withdrawal during the read must not return the previous roster.
        val final = manifest(institutionId, kind, link.externalId)
        if (manifest.sections != final.sections) throw ApiException.DependencyUnavailable("Institutional authority changed during the read")
        policy.live(actor)
        reader.verifyIdentity(current.toUserSummary(), institutionId, link.externalId, kind)
        if (links.findByNativeUserId(institutionId, kind, current.id) !=
            link
        ) {
            throw ApiException.Forbidden("Institutional identity changed")
        }
        return AcademicLoadResponse(
            AcademicProfile(current.id, current.displayName, current.email),
            AcademicScheduleResponse(
                current.id,
                sections.flatMap { section ->
                    section.occurrences
                        .filter {
                            it.status !=
                                ClassOccurrenceStatusContract.CANCELLED
                        }.map { occurrence ->
                            val zone = ZoneId.of(section.timezone)
                            ScheduleEntry(
                                section.id,
                                section.subjectCode,
                                section.subjectName,
                                section.groupName,
                                section.teacherNames.joinToString(" · "),
                                LocalDate.parse(occurrence.date).dayOfWeek.name,
                                Instant
                                    .parse(occurrence.startsAt)
                                    .atZone(zone)
                                    .toLocalTime()
                                    .toString(),
                                Instant
                                    .parse(occurrence.endsAt)
                                    .atZone(zone)
                                    .toLocalTime()
                                    .toString(),
                                occurrence.classroomName,
                                occurrence.buildingName,
                                occurrence.campusName,
                                recurrence = ScheduleRecurrence.ONE_TIME,
                                effectiveDate = occurrence.date,
                            )
                        }
                },
            ),
            sections,
            "SIII",
        )
    }

    private suspend fun manifest(
        institutionId: String,
        kind: InstitutionalIdentityKind,
        externalId: String,
    ): SiiiAuthorityManifest {
        val value = authority.current(institutionId, kind, externalId)
        val verifiedAt = runCatching { Instant.parse(value.verifiedAt) }.getOrNull()
        if (value.contract != "siii.academic_authority.v1" ||
            value.kind != kind.name.lowercase() ||
            value.externalId != externalId ||
            value.sections.size > 1000 ||
            value.sections
                .map { it.sectionId }
                .distinct()
                .size != value.sections.size ||
            value.sections.any {
                it.sourceVersion !in 1..9007199254740991 ||
                    runCatching {
                        java.util.UUID
                            .fromString(it.sectionId)
                            .toString() == it.sectionId
                    }.getOrDefault(false).not()
            } ||
            verifiedAt == null ||
            verifiedAt.isBefore(clock.instant().minusSeconds(60)) ||
            verifiedAt.isAfter(clock.instant().plusSeconds(60))
        ) {
            throw ApiException.DependencyUnavailable("Invalid institutional authority response")
        }
        return value
    }
}

private fun SiiiSectionDetails.toContract() =
    InstitutionalAcademicSection(
        sectionId,
        sectionCode,
        subject.sourceId,
        subject.code,
        subject.name,
        teachers.map { it.fullName },
        timezone,
        sourceVersion,
        syncedAt,
        occurrences.map { occurrence ->
            ClassOccurrenceContract(
                occurrence.sourceId,
                occurrence.patternSourceId,
                sectionId,
                sectionCode,
                subject.code,
                subject.name,
                teachers.joinToString(" · ") { it.fullName },
                occurrence.date,
                occurrence.startsAt,
                occurrence.endsAt,
                ClassOccurrenceStatusContract.valueOf(occurrence.status),
                occurrence.room.code,
                occurrence.room.building,
                occurrence.room.campusName,
            )
        },
    )
