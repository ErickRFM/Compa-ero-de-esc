package org.companerodeescuela.api.attendance

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.companerodeescuela.api.auth.PlatformAccountRepository
import org.companerodeescuela.api.classroom.ClassroomService
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.AttendanceIdentitySource
import org.companerodeescuela.shared.contracts.AttendanceReasonCode
import org.companerodeescuela.shared.contracts.AttendanceRecordResponse
import org.companerodeescuela.shared.contracts.AttendanceReviewEntry
import org.companerodeescuela.shared.contracts.AttendanceSessionStatus
import org.companerodeescuela.shared.contracts.AttendanceStatus
import org.companerodeescuela.shared.contracts.ManualAttendanceMarkRequest
import org.companerodeescuela.shared.contracts.ReviewAttendanceRequest
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary
import java.time.Clock

class AttendanceReviewService(
    private val repository: AttendanceRepository,
    private val accessPolicy: AttendanceAccessPolicy = AttendanceAccessPolicy(repository),
    private val clock: Clock = Clock.systemUTC(),
    private val accounts: PlatformAccountRepository? = null,
    private val classrooms: ClassroomService? = null,
) {
    private val reviewMutex = Mutex()

    /** Creates or corrects a teacher decision, never QR or school-presence evidence. */
    suspend fun markNative(
        reviewer: UserSummary,
        sessionId: String,
        request: ManualAttendanceMarkRequest,
    ): AttendanceRecordResponse =
        reviewMutex.withLock {
            val directory = accounts ?: throw ApiException.DependencyUnavailable("Account directory is unavailable")
            val classes = classrooms ?: throw ApiException.DependencyUnavailable("Classroom authority is unavailable")
            if (UserRole.TEACHER !in reviewer.roles) throw ApiException.Forbidden("Teacher identity is required")
            val session = accessPolicy.requireSession(sessionId)
            accessPolicy.requireOwnerOrAdministrative(reviewer.id, session, false, reviewer)
            if (session.identitySource != AttendanceIdentitySource.NATIVE || session.academicSource != null) {
                throw ApiException.Forbidden("Manual roll call is available only for native classes")
            }
            val now = clock.instant().epochSecond
            if (session.status != AttendanceSessionStatus.OPEN || session.closesAtEpochSeconds <= now) {
                throw ApiException.Conflict("Open attendance session is required")
            }
            val studentId = request.studentId.trim()
            if (studentId.isBlank() || studentId.length > 128 || studentId != request.studentId) {
                throw ApiException.Validation("A valid student account id is required")
            }
            val note = request.note.trim()
            if (note.length !in 3..500 || request.expectedRevision < 0) {
                throw ApiException.Validation("Manual decision requires a reason and current record revision")
            }
            val student =
                directory.findById(studentId)
                    ?: throw ApiException.Forbidden("Student must have a linked account")
            if (
                !student.permitsSession ||
                student.emailVerifiedAt == null ||
                UserRole.STUDENT !in student.roles ||
                student.institutionId != session.institutionId
            ) {
                throw ApiException.Forbidden("Verified student in the same institution is required")
            }
            if (studentId !in classes.activeStudentIdsForTeacher(reviewer, session.courseId)) {
                throw ApiException.Forbidden("Student is not a current member of this class")
            }
            val current = repository.findStudentSessionRecord(session.id, studentId)
            if ((current?.recordRevision ?: 0L) != request.expectedRevision) {
                throw ApiException.Conflict("Attendance revision changed; reload the roster")
            }
            val history =
                AttendanceReviewEntry(
                    reviewerId = reviewer.id,
                    reviewedAtEpochSeconds = now,
                    note = note,
                    status = current?.status ?: AttendanceStatus.REVIEW_REQUIRED,
                    disposition = request.disposition,
                )
            if (current != null) {
                val updated =
                    current.copy(
                        recordRevision = current.recordRevision + 1,
                        disposition = request.disposition,
                        reviewedBy = reviewer.id,
                        reviewedAtEpochSeconds = now,
                        originalStatus = current.originalStatus ?: current.status,
                        originalReasonCode = current.originalReasonCode ?: current.reasonCode,
                        reviewHistory = current.reviewHistory + history,
                    )
                if (!repository.compareAndSetRecord(updated, current.recordRevision)) {
                    throw ApiException.Conflict("Attendance changed while marking manually")
                }
                updated
            } else {
                val id = AttendanceRecordIds.forStudentSession(session.id, studentId)
                val created =
                    AttendanceRecordResponse(
                        id = id,
                        operationId = "manual-rollcall:$id",
                        sessionId = session.id,
                        occurrenceId = session.occurrenceId,
                        studentId = studentId,
                        status = AttendanceStatus.REVIEW_REQUIRED,
                        reasonCode = AttendanceReasonCode.TEACHER_REVIEW,
                        attemptedAtEpochSeconds = now,
                        receivedAtEpochSeconds = now,
                        reviewedBy = reviewer.id,
                        reviewedAtEpochSeconds = now,
                        disposition = request.disposition,
                        originalStatus = AttendanceStatus.REVIEW_REQUIRED,
                        originalReasonCode = AttendanceReasonCode.TEACHER_REVIEW,
                        reviewHistory = listOf(history),
                    )
                when (val result = repository.writeAttempt(created)) {
                    is AttemptWriteResult.Created -> result.record
                    is AttemptWriteResult.Existing -> throw ApiException.Conflict("Attendance exists; reload the roster")
                    AttemptWriteResult.OperationConflict -> throw ApiException.Conflict("Attendance operation is already used")
                }
            }
        }

    suspend fun review(
        reviewerId: String,
        recordId: String,
        request: ReviewAttendanceRequest,
        allowCrossOwner: Boolean = false,
        nativeActor: org.companerodeescuela.shared.contracts.UserSummary? = null,
    ): AttendanceRecordResponse =
        reviewMutex.withLock {
            val record =
                repository
                    .findRecord(recordId)
                    ?.withCurrentPresencePolicy()
                    ?: throw ApiException.NotFound("Attendance record was not found")
            val session = accessPolicy.requireSession(record.sessionId)
            accessPolicy.requireOwnerOrAdministrative(reviewerId, session, allowCrossOwner, nativeActor)
            val note =
                request.note?.trim() ?: request.reasonCode.name
                    .lowercase()
                    .replace('_', ' ')
            if (note.length !in 3..500) throw ApiException.Validation("A review reason between 3 and 500 characters is required")

            val reviewed =
                record.copy(
                    recordRevision = record.recordRevision + 1,
                    status = if (request.disposition == null) request.status else record.status,
                    reasonCode = if (request.disposition == null) request.reasonCode else record.reasonCode,
                    disposition = request.disposition ?: record.disposition,
                    reviewedBy = reviewerId,
                    reviewedAtEpochSeconds = clock.instant().epochSecond,
                    originalStatus = record.originalStatus ?: record.status,
                    originalReasonCode = record.originalReasonCode ?: record.reasonCode,
                    reviewHistory =
                        record.reviewHistory +
                            AttendanceReviewEntry(
                                reviewerId,
                                clock.instant().epochSecond,
                                note,
                                if (request.disposition == null) request.status else record.status,
                                request.disposition ?: record.disposition,
                            ),
                )
            if (!repository.compareAndSetRecord(reviewed, record.recordRevision)) {
                throw ApiException.Conflict("Attendance changed during review; reload before submitting another decision")
            }
            reviewed
        }
}
