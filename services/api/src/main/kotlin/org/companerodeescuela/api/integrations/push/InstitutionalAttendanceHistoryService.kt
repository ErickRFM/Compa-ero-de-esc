package org.companerodeescuela.api.integrations.push

import org.companerodeescuela.api.attendance.AttendanceStudentService
import org.companerodeescuela.api.attendance.SiiiAttendanceAuthority
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.AttendanceIdentitySource
import org.companerodeescuela.shared.contracts.InstitutionalAttendanceHistoryContract
import org.companerodeescuela.shared.contracts.InstitutionalAttendanceHistoryRecord
import org.companerodeescuela.shared.contracts.InstitutionalAttendanceHistoryRequest
import org.companerodeescuela.shared.contracts.InstitutionalAttendanceHistoryResponse
import org.companerodeescuela.shared.contracts.InstitutionalIdentityKind
import java.security.MessageDigest
import java.time.Clock

class InstitutionalAttendanceHistoryService(
    private val identities: InstitutionalIdentityLinkService,
    private val students: AttendanceStudentService,
    private val authority: SiiiAttendanceAuthority?,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun read(
        institution: VerifiedInstitution,
        request: InstitutionalAttendanceHistoryRequest,
    ): InstitutionalAttendanceHistoryResponse {
        if (InstitutionalAttendanceHistoryContract.SCOPE !in institution.scopes) {
            throw ApiException.Forbidden("Institutional attendance read scope is required")
        }
        if (request.contract != InstitutionalAttendanceHistoryContract.REQUEST ||
            request.studentExternalId.isBlank() ||
            request.studentExternalId != request.studentExternalId.trim() ||
            request.studentExternalId.length > 256 ||
            request.studentExternalId.any(Char::isISOControl)
        ) {
            throw ApiException.Validation("A bounded institutional student reference and supported contract are required")
        }
        val student =
            identities.resolve(institution.tenantId, InstitutionalIdentityKind.STUDENT, request.studentExternalId)
                ?: throw ApiException.NotFound("Current institutional student linkage was not found")
        val actor = student.toUserSummary()
        if (authority == null || !authority.configured(actor)) {
            throw ApiException.DependencyUnavailable("Institutional attendance authority is unavailable")
        }
        return students.withHistory(student.id, actor) { entries ->
            val records =
                entries
                    .map { (record, session) ->
                        if (record.studentId != student.id ||
                            session.institutionId != institution.tenantId ||
                            session.identitySource != AttendanceIdentitySource.NATIVE ||
                            session.academicSource != SiiiAttendanceAuthority.SOURCE ||
                            session.groupId != session.courseId ||
                            session.groupScopeRevision < 1 ||
                            record.sessionId != session.id ||
                            record.occurrenceId != session.occurrenceId
                        ) {
                            throw ApiException.Forbidden("Attendance is outside the authenticated institution's source scope")
                        }
                        InstitutionalAttendanceHistoryRecord(
                            auditRecordId =
                                MessageDigest
                                    .getInstance("SHA-256")
                                    .digest(
                                        (
                                            "siii.attendance.audit.v1|" +
                                                "${institution.tenantId.length}:${institution.tenantId}" +
                                                "${record.id.length}:${record.id}"
                                        ).toByteArray(Charsets.UTF_8),
                                    ).joinToString("") { "%02x".format(it) },
                            sessionId = session.id,
                            sectionId = session.courseId,
                            occurrenceId = session.occurrenceId,
                            sourceVersion = session.groupScopeRevision,
                            occurrenceDate = session.occurrenceDate,
                            status = record.status,
                            disposition = record.disposition,
                            reasonCode = record.reasonCode,
                            receivedAtEpochSeconds = record.receivedAtEpochSeconds,
                            reviewedAtEpochSeconds = record.reviewedAtEpochSeconds,
                        )
                    }.take(InstitutionalAttendanceHistoryContract.SAMPLE_LIMIT)
            val current = identities.resolve(institution.tenantId, InstitutionalIdentityKind.STUDENT, request.studentExternalId)
            if (current == null ||
                current.id != student.id ||
                current.authRevision != student.authRevision ||
                current.roles != student.roles
            ) {
                throw ApiException.Forbidden("Institutional student authority changed during the history read")
            }
            InstitutionalAttendanceHistoryResponse(
                InstitutionalAttendanceHistoryContract.RESPONSE,
                clock.instant().epochSecond,
                InstitutionalAttendanceHistoryContract.SAMPLE_LIMIT,
                records,
            )
        }
    }
}
