package org.companerodeescuela.shared.contracts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ChannelType {
    @SerialName("class")
    CLASS,

    @SerialName("representatives")
    REPRESENTATIVES,
}

@Serializable
enum class ChannelPostType {
    @SerialName("announcement")
    ANNOUNCEMENT,

    @SerialName("material")
    MATERIAL,

    @SerialName("reminder")
    REMINDER,

    @SerialName("room_change")
    ROOM_CHANGE,

    @SerialName("schedule_change")
    SCHEDULE_CHANGE,

    @SerialName("class_cancelled")
    CLASS_CANCELLED,

    @SerialName("event")
    EVENT,

    @SerialName("meeting")
    MEETING,
}

@Serializable
enum class ChannelPostState {
    @SerialName("published")
    PUBLISHED,

    @SerialName("edited")
    EDITED,

    @SerialName("deleted")
    DELETED,
}

@Serializable
enum class ChannelPresetResponse {
    @SerialName("acknowledged")
    ACKNOWLEDGED,

    @SerialName("confirmed")
    CONFIRMED,

    @SerialName("will_attend")
    WILL_ATTEND,

    @SerialName("cannot_attend")
    CANNOT_ATTEND,

    @SerialName("need_clarification")
    NEED_CLARIFICATION,
}

@Serializable
enum class ChannelAttachmentType {
    @SerialName("link")
    LINK,

    @SerialName("document")
    DOCUMENT,

    @SerialName("image")
    IMAGE,
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

@Serializable
enum class TaskStatus {
    @SerialName("pending")
    PENDING,

    @SerialName("submitting")
    SUBMITTING,

    @SerialName("submitted")
    SUBMITTED,

    @SerialName("overdue")
    OVERDUE,

    @SerialName("returned")
    RETURNED,

    @SerialName("reviewed")
    REVIEWED,

    @SerialName("error")
    ERROR,
}

@Serializable
data class ChannelTask(
    val id: String,
    val channelId: String,
    val teacherId: String,
    val teacherDisplayName: String,
    val title: String,
    val instructions: String,
    val dueDateEpochSeconds: Long,
    val attachments: List<ChannelAttachment> = emptyList(),
    val createdAtEpochSeconds: Long,
    val closed: Boolean = false,
    val version: Int = 1,
    val files: List<ChannelPrivateFileMetadata> = emptyList(),
)

@Serializable
data class TaskSubmission(
    val id: String,
    val taskId: String,
    val channelId: String,
    val studentId: String,
    val studentDisplayName: String,
    val status: TaskStatus,
    val comment: String? = null,
    val attachments: List<ChannelAttachment> = emptyList(),
    val submittedAtEpochSeconds: Long,
    val feedback: String? = null,
    val reviewedAtEpochSeconds: Long? = null,
    val version: Int = 1,
    val history: List<TaskSubmissionVersion> = emptyList(),
    val files: List<ChannelPrivateFileMetadata> = emptyList(),
    val fileIds: List<String> = emptyList(),
    @kotlinx.serialization.Transient val idempotencyKeys: Set<String> = emptySet(),
)

@Serializable
data class CreateChannelTaskRequest(
    val title: String,
    val instructions: String,
    val dueDateEpochSeconds: Long,
    val attachments: List<ChannelAttachment> = emptyList(),
    val fileIds: List<String> = emptyList(),
)

@Serializable
data class UpdateChannelTaskRequest(
    val title: String? = null,
    val instructions: String? = null,
    val dueDateEpochSeconds: Long? = null,
    val attachments: List<ChannelAttachment>? = null,
    val fileIds: List<String>? = null,
    val closed: Boolean? = null,
    val expectedVersion: Int,
)

@Serializable
data class SubmitTaskRequest(
    val comment: String? = null,
    val attachments: List<ChannelAttachment> = emptyList(),
    val fileIds: List<String> = emptyList(),
    val idempotencyKey: String? = null,
    val expectedVersion: Int? = null,
    val submissionId: String? = null,
)

@Serializable
data class ReviewSubmissionRequest(
    val status: TaskStatus,
    val feedback: String? = null,
    val expectedVersion: Int,
)

@Serializable
data class TaskSubmissionVersion(
    val version: Int,
    val status: TaskStatus,
    val comment: String? = null,
    val fileIds: List<String> = emptyList(),
    val feedback: String? = null,
    val changedBy: String,
    val changedAtEpochSeconds: Long,
)

@Serializable
enum class ChannelFileState {
    @SerialName("uploading")
    UPLOADING,

    @SerialName("available")
    AVAILABLE,
}

@Serializable
enum class ChannelFileScope {
    @SerialName("task")
    TASK,

    @SerialName("submission")
    SUBMISSION,
}

@Serializable
data class ChannelPrivateFileMetadata(
    val id: String,
    val channelId: String,
    val taskId: String,
    val submissionId: String? = null,
    val ownerId: String,
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long,
    val uploadedBytes: Long,
    val state: ChannelFileState,
    val scope: ChannelFileScope,
    val createdAtEpochSeconds: Long,
    val downloadPath: String,
)

@Serializable
data class CreateChannelFileRequest(
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long,
)

@Serializable
data class ChannelFileUploadProgress(
    val fileId: String,
    val uploadedBytes: Long,
    val totalBytes: Long,
    val complete: Boolean,
    val uploadPath: String,
)
