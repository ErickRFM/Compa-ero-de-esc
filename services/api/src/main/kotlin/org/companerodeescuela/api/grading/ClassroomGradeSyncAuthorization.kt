package org.companerodeescuela.api.grading

import org.companerodeescuela.api.classroom.ClassroomService
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.GradeSyncRequest
import org.companerodeescuela.shared.contracts.UserSummary

/** Ensures a grade export is limited to the teacher's current class and roster. */
class ClassroomGradeSyncAuthorization(
    private val classrooms: ClassroomService,
) {
    suspend fun requireCanSync(
        actor: UserSummary,
        request: GradeSyncRequest,
    ) {
        val enrolledStudents = classrooms.studentIdsForTeacher(actor, request.classroomId).toSet()
        if (request.rows.any { it.studentId !in enrolledStudents }) {
            throw ApiException.Forbidden("Grades can only be synchronized for students in the assigned classroom")
        }
    }
}
