package org.companerodeescuela.shared.contracts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AcademicEventType {
    @SerialName("class_cancelled") CLASS_CANCELLED,
    @SerialName("class_rescheduled") CLASS_RESCHEDULED,
    @SerialName("room_changed") ROOM_CHANGED,
    @SerialName("class_online") CLASS_ONLINE,
    @SerialName("announcement") ANNOUNCEMENT,
    @SerialName("event") EVENT,
    @SerialName("conference") CONFERENCE,
    @SerialName("assignment_notice") ASSIGNMENT_NOTICE,
}

@Serializable
enum class AcademicEventSource {
    @SerialName("institution") INSTITUTION,
    @SerialName("teacher") TEACHER,
    @SerialName("coordinator") COORDINATOR,
    @SerialName("admin") ADMIN,
}

@Serializable
enum class AcademicEventPriority {
    @SerialName("normal") NORMAL,
    @SerialName("important") IMPORTANT,
    @SerialName("urgent") URGENT,
}

@Serializable
enum class AcademicEventScope {
    @SerialName("institution") INSTITUTION,
    @SerialName("career") CAREER,
    @SerialName("group") GROUP,
    @SerialName("course") COURSE,
    @SerialName("class_occurrence") CLASS_OCCURRENCE,
    @SerialName("student") STUDENT,
}

@Serializable
data class AcademicEventTarget(
    val scope: AcademicEventScope,
    val id: String,
)

@Serializable
data class AcademicEvent(
    val id: String,
    val type: AcademicEventType,
    val source: AcademicEventSource,
    val sourceId: String,
    val sourceDisplayName: String? = null,
    val target: AcademicEventTarget,
    val occurrenceId: String? = null,
    val courseId: String? = null,
    val subjectName: String? = null,
    val groupName: String? = null,
    val title: String,
    val body: String? = null,
    val priority: AcademicEventPriority = AcademicEventPriority.NORMAL,
    val previousStartsAt: String? = null,
    val newStartsAt: String? = null,
    val previousEndsAt: String? = null,
    val newEndsAt: String? = null,
    val previousRoom: String? = null,
    val newRoom: String? = null,
    val onlineUrl: String? = null,
    val effectiveAtEpochSeconds: Long? = null,
    val createdAtEpochSeconds: Long,
    val expiresAtEpochSeconds: Long? = null,
)

@Serializable
data class AcademicEventFeedResponse(
    val events: List<AcademicEvent>,
    val generatedAtEpochSeconds: Long,
)
