package org.companerodeescuela.api.attendance

import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.AttendanceSessionResponse

class AttendanceAccessPolicy(
    private val repository: AttendanceRepository,
) {
    suspend fun requireSession(sessionId: String): AttendanceSessionResponse =
        repository.findSession(sessionId)
            ?: throw ApiException.NotFound("Attendance session was not found")

    fun requireOwnerOrAdministrative(
        actorId: String,
        session: AttendanceSessionResponse,
        allowCrossOwner: Boolean,
    ) {
        if (!allowCrossOwner && session.openedBy != actorId) {
            throw ApiException.Forbidden("This attendance session belongs to another teacher")
        }
    }
}
