package org.companerodeescuela.api.integrations.push

import org.companerodeescuela.api.attendance.AttendanceStudentService
import org.companerodeescuela.api.attendance.SiiiAttendanceAuthority
import org.companerodeescuela.api.attendance.withCurrentPresencePolicy
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
    private val archivalRecords: org.companerodeescuela.api.attendance.AttendanceRepository? = null,
) {
    suspend fun read(
        institution: VerifiedInstitution,
        request: InstitutionalAttendanceHistoryRequest,
    ): InstitutionalAttendanceHistoryResponse {
        val archival = request.contract == InstitutionalAttendanceHistoryContract.ARCHIVE_REQUEST
        val scope = if (archival) InstitutionalAttendanceHistoryContract.ARCHIVE_SCOPE else InstitutionalAttendanceHistoryContract.SCOPE
        if (scope !in institution.scopes) {
            throw ApiException.Forbidden("Institutional attendance read scope is required")
        }
        val versioned = archival || request.contract == InstitutionalAttendanceHistoryContract.REQUEST_V2
        if ((request.page != null && (!versioned || request.page !in 1..10_000))) {
            throw ApiException.Validation("A bounded page is available only for the versioned history contract")
        }
        if ((request.contract != InstitutionalAttendanceHistoryContract.REQUEST && !versioned) ||
            (!archival && (request.studentExternalId.isBlank() || request.teacherExternalId != null)) ||
            request.studentExternalId != request.studentExternalId.trim() ||
            request.studentExternalId.length > 256 ||
            request.studentExternalId.any(Char::isISOControl)
        ) {
            throw ApiException.Validation("A bounded institutional student reference and supported contract are required")
        }
        if (archival) return readArchive(institution, request)
        val student =
            identities.resolve(institution.tenantId, InstitutionalIdentityKind.STUDENT, request.studentExternalId)
                ?: throw ApiException.NotFound("Current institutional student linkage was not found")
        val actor = student.toUserSummary()
        if (authority == null || !authority.configured(actor)) {
            throw ApiException.DependencyUnavailable("Institutional attendance authority is unavailable")
        }
        var nextPage: Int? = null
        return students.withHistory(
            student.id,
            actor,
            page = if (versioned) request.page ?: 1 else null,
            nextPage = { nextPage = it },
        ) { entries ->
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
                            recordRevision = record.recordRevision.takeIf { versioned },
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
                if (versioned) InstitutionalAttendanceHistoryContract.RESPONSE_V2 else InstitutionalAttendanceHistoryContract.RESPONSE,
                clock.instant().epochSecond,
                InstitutionalAttendanceHistoryContract.SAMPLE_LIMIT,
                records,
                page = (request.page ?: 1).takeIf { versioned },
                nextPage = nextPage,
            )
        }
    }

    /** A separately authorized institution exports its own retained records. The receiver must
     * validate the original frozen SIII snapshot; current mobile access remains independently denied.
     */
    private suspend fun readArchive(
        institution: VerifiedInstitution,
        request: InstitutionalAttendanceHistoryRequest,
    ): InstitutionalAttendanceHistoryResponse {
        val repository = archivalRecords ?: throw ApiException.DependencyUnavailable("Institutional archive export is unavailable")
        val teacher = request.teacherExternalId
        if ((teacher == null) ==
            request.studentExternalId.isBlank()
        ) {
            throw ApiException.Validation("Exactly one archival subject is required")
        }
        if (teacher != null &&
            (teacher.isBlank() || teacher != teacher.trim() || teacher.length > 256 || teacher.any(Char::isISOControl))
        ) {
            throw ApiException.Validation("Invalid archival teacher reference")
        }

        suspend fun subjectId(): String? =
            if (teacher ==
                null
            ) {
                identities.historicalStudentId(institution.tenantId, request.studentExternalId)
            } else {
                identities.historicalTeacherId(institution.tenantId, teacher)
            }
        val nativeId = subjectId() ?: throw ApiException.NotFound("An approved historical institutional linkage is required")
        val page = request.page ?: 1
        val candidates = if (teacher == null) repository.studentHistoryPage(nativeId, page) else emptyList()
        val sessions = linkedMapOf<String, org.companerodeescuela.shared.contracts.AttendanceSessionResponse>()
        val teacherSessions = if (teacher != null) repository.teacherHistoryPage(nativeId, page) else emptyList()
        for (session in teacherSessions.take(100)) if (session.openedBy == nativeId) sessions[session.id] = session
        val exported =
            candidates.take(100).mapNotNull { raw ->
                val session = repository.findSession(raw.sessionId) ?: return@mapNotNull null
                if (raw.studentId != nativeId ||
                    !archivalScope(session, institution.tenantId) ||
                    raw.occurrenceId != session.occurrenceId
                ) {
                    return@mapNotNull null
                }
                sessions[session.id] = session
                val record = raw.withCurrentPresencePolicy()
                InstitutionalAttendanceHistoryRecord(
                    auditRecordId =
                        MessageDigest
                            .getInstance("SHA-256")
                            .digest(
                                (
                                    "siii.attendance.audit.v1|" +
                                        "${institution.tenantId.length}:${institution.tenantId}" + "${record.id.length}:${record.id}"
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
                    recordRevision = record.recordRevision,
                )
            }
        val classSessions =
            sessions.values.mapNotNull { session ->
                if (!archivalScope(session, institution.tenantId) || repository.closureWork(session.id) != null) return@mapNotNull null
                val reference = identities.historicalTeacherExternalId(institution.tenantId, session.openedBy) ?: return@mapNotNull null
                org.companerodeescuela.shared.contracts.InstitutionalClassSessionRecord(
                    session.id,
                    session.courseId,
                    session.occurrenceId,
                    session.groupScopeRevision,
                    session.occurrenceDate,
                    reference,
                    if (session.status == org.companerodeescuela.shared.contracts.AttendanceSessionStatus.CLOSED) 2 else 1,
                    session.status,
                    session.openedAtEpochSeconds,
                    session.closedAtEpochSeconds,
                    session.closedByTeacher && session.closedAtEpochSeconds != null,
                )
            }
        if (subjectId() != nativeId) throw ApiException.Forbidden("Institutional linkage changed during export")
        val hasMore = if (teacher == null) candidates.size > 100 else teacherSessions.size > 100
        return InstitutionalAttendanceHistoryResponse(
            InstitutionalAttendanceHistoryContract.ARCHIVE_RESPONSE,
            clock.instant().epochSecond,
            100,
            exported,
            page,
            (page + 1).takeIf { hasMore },
            classSessions,
        )
    }

    private fun archivalScope(
        session: org.companerodeescuela.shared.contracts.AttendanceSessionResponse,
        institutionId: String,
    ): Boolean =
        session.institutionId == institutionId &&
            session.identitySource == AttendanceIdentitySource.NATIVE &&
            session.academicSource == SiiiAttendanceAuthority.SOURCE &&
            session.groupId == session.courseId &&
            session.groupScopeRevision > 0
}
