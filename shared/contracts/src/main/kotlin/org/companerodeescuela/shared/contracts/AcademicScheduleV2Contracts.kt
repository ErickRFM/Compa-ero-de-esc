package org.companerodeescuela.shared.contracts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ClassOccurrenceStatusContract {
    @SerialName("scheduled")
    SCHEDULED,

    @SerialName("cancelled")
    CANCELLED,

    @SerialName("rescheduled")
    RESCHEDULED,

    @SerialName("online")
    ONLINE,
}

@Serializable
enum class ScheduleChangeKindContract {
    @SerialName("time_changed")
    TIME_CHANGED,

    @SerialName("room_changed")
    ROOM_CHANGED,

    @SerialName("teacher_changed")
    TEACHER_CHANGED,

    @SerialName("cancelled")
    CANCELLED,

    @SerialName("moved_online")
    MOVED_ONLINE,
}

@Serializable
data class ScheduleChangeContract(
    val kind: ScheduleChangeKindContract,
    val note: String? = null,
    val originalDate: String? = null,
    val originalStartsAt: String? = null,
    val originalEndsAt: String? = null,
    val originalClassroomName: String? = null,
    val originalTeacherName: String? = null,
)

@Serializable
data class ClassOccurrenceContract(
    val id: String,
    val patternId: String?,
    val courseId: String,
    val groupName: String,
    val subjectCode: String,
    val subjectName: String,
    val teacherName: String,
    val date: String,
    val startsAt: String,
    val endsAt: String,
    val status: ClassOccurrenceStatusContract,
    val classroomName: String? = null,
    val buildingName: String? = null,
    val campusName: String? = null,
    val changes: List<ScheduleChangeContract> = emptyList(),
)

@Serializable
data class AcademicWeekResponse(
    val ownerId: String,
    val weekStartsOn: String,
    val weekEndsOn: String,
    val occurrences: List<ClassOccurrenceContract>,
)
