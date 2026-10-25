package org.companerodeescuela.shared.contracts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ScheduleSource {
    @SerialName("institutional")
    INSTITUTIONAL,

    @SerialName("manual")
    MANUAL,

    @SerialName("ocr_import")
    OCR_IMPORT,
}

@Serializable
data class AcademicProfile(
    val id: String,
    val displayName: String,
    val email: String? = null,
)

@Serializable
data class ScheduleEntry(
    val courseId: String,
    val subjectCode: String,
    val subjectName: String,
    val groupName: String,
    val teacherName: String,
    val dayOfWeek: String,
    val startsAt: String,
    val endsAt: String,
    val classroomName: String? = null,
    val buildingName: String? = null,
    val campusName: String? = null,
    val source: ScheduleSource = ScheduleSource.INSTITUTIONAL,
    val recurrence: ScheduleRecurrence = ScheduleRecurrence.WEEKLY,
    val seriesId: String? = null,
    val effectiveDate: String? = null,
)

@Serializable
data class AcademicScheduleResponse(
    val ownerId: String,
    val entries: List<ScheduleEntry>,
)

@Serializable
data class AcademicLoadResponse(
    val student: AcademicProfile,
    val schedule: AcademicScheduleResponse,
    val institutionalSections: List<InstitutionalAcademicSection> = emptyList(),
    val institutionalSource: String? = null,
)

/** Authorized institutional catalog; does not expose student rosters. */
@Serializable
data class InstitutionalAcademicSection(
    val id: String,
    val groupName: String,
    val subjectId: String,
    val subjectCode: String,
    val subjectName: String,
    val teacherNames: List<String>,
    val timezone: String,
    val sourceVersion: Long,
    val syncedAt: String,
    val occurrences: List<ClassOccurrenceContract>,
)
