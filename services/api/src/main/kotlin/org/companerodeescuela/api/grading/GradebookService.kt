package org.companerodeescuela.api.grading

import org.companerodeescuela.api.classroom.ClassroomService
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.GradebookDraftRequest
import org.companerodeescuela.shared.contracts.GradebookRevision
import org.companerodeescuela.shared.contracts.GradebookSnapshot
import org.companerodeescuela.shared.contracts.GradebookStatus
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary
import java.time.Clock

class GradebookService(
    private val classrooms: ClassroomService,
    private val repository: GradebookRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun saveDraft(
        actor: UserSummary,
        request: GradebookDraftRequest,
    ): GradebookSnapshot {
        requireTeacher(actor)
        validate(request)
        classrooms.requireCanPublish(actor, request.classroomId)
        val group = classrooms.scopedGroupFor(actor, request.classroomId)
        val enrolled = classrooms.activeStudentIdsForTeacher(actor, request.classroomId).toSet()
        if (request.rows.any { it.studentId !in enrolled }) {
            throw ApiException.Forbidden("Grades can only be saved for students in the current classroom roster")
        }
        val current = repository.find(request.classroomId, request.gradingPeriod)
        if (request.expectedRevision == null && current != null) {
            throw ApiException.Conflict("Gradebook already exists; reload its current revision")
        }
        if (request.expectedRevision != null && current?.revision != request.expectedRevision) {
            throw ApiException.Conflict("Gradebook changed; reload its current revision")
        }
        if (current?.status == GradebookStatus.PUBLISHED) {
            throw ApiException.Conflict("Published grades cannot be edited in this operation")
        }
        val now = clock.instant().epochSecond
        val revision = (current?.revision ?: 0L) + 1L
        val snapshot =
            GradebookSnapshot(
                classroomId = request.classroomId,
                academicPeriodId = group.periodId ?: throw ApiException.Forbidden("Current academic period is required"),
                gradingPeriod = request.gradingPeriod,
                teacherId = actor.id,
                revision = revision,
                status = GradebookStatus.DRAFT,
                categories = request.categories,
                rows = request.rows,
                updatedAtEpochSeconds = now,
                history =
                    current?.history.orEmpty() +
                        GradebookRevision(
                            revision = revision,
                            status = GradebookStatus.DRAFT,
                            changedBy = actor.id,
                            changedAtEpochSeconds = now,
                            categories = request.categories,
                            rows = request.rows,
                        ),
            )
        if (!repository.saveDraft(snapshot, request.expectedRevision)) {
            throw ApiException.Conflict("Gradebook changed; reload its current revision")
        }
        return snapshot
    }

    suspend fun publish(
        actor: UserSummary,
        classroomId: String,
        gradingPeriod: String,
        expectedRevision: Long,
    ): GradebookSnapshot {
        requireTeacher(actor)
        classrooms.requireCanPublish(actor, classroomId)
        val current = repository.find(classroomId, gradingPeriod) ?: throw ApiException.NotFound("Draft gradebook was not found")
        if (current.teacherId != actor.id) throw ApiException.Forbidden("Only the assigned teacher can publish this gradebook")
        if (current.status != GradebookStatus.DRAFT || current.revision != expectedRevision) {
            throw ApiException.Conflict("Only the current draft revision can be published")
        }
        return repository.publish(classroomId, gradingPeriod, expectedRevision, actor.id, clock.instant().epochSecond)
            ?: throw ApiException.Conflict("Gradebook changed before it could be published")
    }

    suspend fun teacherView(
        actor: UserSummary,
        classroomId: String,
        gradingPeriod: String,
    ): GradebookSnapshot? {
        requireTeacher(actor)
        classrooms.requireCanPublish(actor, classroomId)
        return repository.find(classroomId, gradingPeriod)?.also {
            if (it.teacherId != actor.id) throw ApiException.Forbidden("Only the assigned teacher can read this gradebook")
        }
    }

    suspend fun studentView(
        actor: UserSummary,
        classroomId: String,
        gradingPeriod: String,
    ): GradebookSnapshot? {
        if (UserRole.STUDENT !in actor.roles) throw ApiException.Forbidden("Student account is required")
        classrooms.requireCanRead(actor, classroomId)
        val snapshot = repository.find(classroomId, gradingPeriod) ?: return null
        if (snapshot.status != GradebookStatus.PUBLISHED) return null
        val ownRow = snapshot.rows.firstOrNull { it.studentId == actor.id } ?: return null
        return snapshot.copy(rows = listOf(ownRow), history = emptyList())
    }

    private fun validate(request: GradebookDraftRequest) {
        if (!validText(request.classroomId, 128) || !validText(request.gradingPeriod, 80)) {
            throw ApiException.Validation("A valid classroom and grading period are required")
        }
        val expectedRevision = request.expectedRevision
        if (expectedRevision != null && expectedRevision < 1) {
            throw ApiException.Validation("expectedRevision must be positive")
        }
        if (request.categories.isEmpty() || request.categories.size > MAX_CATEGORIES) {
            throw ApiException.Validation("At least one and at most 30 grade categories are required")
        }
        if (request.categories.any {
                !validText(it.id, 64) ||
                    !validText(it.name, 100) ||
                    !it.weightPercent.isFinite() ||
                    it.weightPercent <= 0.0 ||
                    it.weightPercent > 100.0
            } ||
            request.categories
                .map { it.id }
                .distinct()
                .size != request.categories.size
        ) {
            throw ApiException.Validation("Grade categories must have unique ids, names and weights between 0 and 100")
        }
        if (kotlin.math.abs(request.categories.sumOf { it.weightPercent } - 100.0) > WEIGHT_TOLERANCE) {
            throw ApiException.Validation("Grade category weights must sum to 100 percent")
        }
        if (request.rows.isEmpty() ||
            request.rows.size > MAX_ROWS ||
            request.rows.any {
                !validText(it.studentId, 128) ||
                    it.scores.keys.any { key -> request.categories.none { category -> category.id == key } }
            } ||
            request.rows
                .map { it.studentId }
                .distinct()
                .size != request.rows.size
        ) {
            throw ApiException.Validation("Grade rows must contain unique current student ids and known categories")
        }
        if (request.rows.any { row -> row.scores.values.any { score -> score != null && (!score.isFinite() || score !in 0.0..10.0) } }) {
            throw ApiException.Validation("Each grade must be between 0 and 10")
        }
    }

    private fun requireTeacher(actor: UserSummary) {
        if (UserRole.TEACHER !in actor.roles) throw ApiException.Forbidden("Teacher account is required")
    }

    private fun validText(
        value: String,
        maxLength: Int,
    ): Boolean = value.isNotBlank() && value == value.trim() && value.length <= maxLength && value.none(Char::isISOControl)

    private companion object {
        const val MAX_CATEGORIES = 30
        const val MAX_ROWS = 500
        const val WEIGHT_TOLERANCE = 0.001
    }
}
