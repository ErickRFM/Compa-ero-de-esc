package org.companerodeescuela.shared.contracts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ChannelType {
    @SerialName("class") CLASS,
    @SerialName("representatives") REPRESENTATIVES,
}

@Serializable
enum class ChannelPostType {
    @SerialName("announcement") ANNOUNCEMENT,
    @SerialName("material") MATERIAL,
    @SerialName("reminder") REMINDER,
    @SerialName("room_change") ROOM_CHANGE,
    @SerialName("schedule_change") SCHEDULE_CHANGE,
    @SerialName("class_cancelled") CLASS_CANCELLED,
    @SerialName("event") EVENT,
    @SerialName("meeting") MEETING,
}

@Serializable
enum class ChannelPostState {
    @SerialName("published") PUBLISHED,
    @SerialName("edited") EDITED,
    @SerialName("deleted") DELETED,
}

@Serializable
enum class ChannelPresetResponse {
    @SerialName("acknowledged") ACKNOWLEDGED,
    @SerialName("confirmed") CONFIRMED,
    @SerialName("will_attend") WILL_ATTEND,
    @SerialName("cannot_attend") CANNOT_ATTEND,
    @SerialName("need_clarification") NEED_CLARIFICATION,
}

@Serializable
enum class ChannelAttachmentType {
    @SerialName("link") LINK,
    @SerialName("document") DOCUMENT,
    @SerialName("image") IMAGE,
}

@Serializable
data class ChannelAttachment(
    val type: ChannelAttachmentType,
    val label: String,
    val url: String,
)

@Serializable
data class ClassChannelSummary(
    val id: String,
    val courseId: String,
    val subjectCode: String,
    val subjectName: String,
    val groupName: String,
    val term: String,
    val teacherId: String,
    val teacherDisplayName: String,
    val canPublish: Boolean,
    val channelType: ChannelType = ChannelType.CLASS,
)

@Serializable
data class ChannelPost(
    val id: String,
    val channelId: String,
    val authorId: String,
    val authorDisplayName: String,
    val type: ChannelPostType,
    val title: String? = null,
    val body: String? = null,
    val attachments: List<ChannelAttachment> = emptyList(),
    val allowedResponses: Set<ChannelPresetResponse> = setOf(ChannelPresetResponse.ACKNOWLEDGED),
    val pinned: Boolean = false,
    val state: ChannelPostState = ChannelPostState.PUBLISHED,
    val createdAtEpochSeconds: Long,
    val editedAtEpochSeconds: Long? = null,
)

@Serializable
data class CreateChannelPostRequest(
    val type: ChannelPostType = ChannelPostType.ANNOUNCEMENT,
    val title: String? = null,
    val body: String? = null,
    val attachments: List<ChannelAttachment> = emptyList(),
    val allowedResponses: Set<ChannelPresetResponse> = setOf(ChannelPresetResponse.ACKNOWLEDGED),
    val pinned: Boolean = false,
)

@Serializable
data class UpdateChannelPostRequest(
    val title: String? = null,
    val body: String? = null,
    val attachments: List<ChannelAttachment>? = null,
    val allowedResponses: Set<ChannelPresetResponse>? = null,
    val pinned: Boolean? = null,
)

@Serializable
data class ChannelAcknowledgementRequest(
    val response: ChannelPresetResponse? = null,
)

@Serializable
data class ChannelAcknowledgement(
    val postId: String,
    val studentId: String,
    val response: ChannelPresetResponse? = null,
    val viewedAtEpochSeconds: Long,
    val respondedAtEpochSeconds: Long? = null,
)

@Serializable
data class ChannelPostStats(
    val postId: String,
    val viewed: Int,
    val responded: Int,
    val byResponse: Map<ChannelPresetResponse, Int>,
)
