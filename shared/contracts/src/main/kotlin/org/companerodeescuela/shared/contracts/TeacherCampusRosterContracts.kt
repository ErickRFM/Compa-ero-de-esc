package org.companerodeescuela.shared.contracts

import kotlinx.serialization.Serializable

/**
 * School-entry evidence for one explicitly authorized academic occurrence.
 * Null entry time means no active school-day evidence; it does NOT prove absence.
 */
@Serializable
data class CampusRosterStudent(
    val studentId: String,
    val campusEntryAtEpochSeconds: Long? = null,
    val classRecord: AttendanceRecordResponse? = null,
)

@Serializable
data class TeacherCampusRosterResponse(
    val occurrenceId: String,
    val groupName: String,
    val sessionId: String? = null,
    val students: List<CampusRosterStudent>,
)
