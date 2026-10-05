package org.companerodeescuela.api.channel

import java.time.Clock
import java.util.UUID
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.ChannelAcknowledgement
import org.companerodeescuela.shared.contracts.ChannelAcknowledgementRequest
import org.companerodeescuela.shared.contracts.ChannelAttachment
import org.companerodeescuela.shared.contracts.ChannelPost
import org.companerodeescuela.shared.contracts.ChannelPostState
import org.companerodeescuela.shared.contracts.ChannelPostStats
import org.companerodeescuela.shared.contracts.ChannelPresetResponse
import org.companerodeescuela.shared.contracts.CreateChannelPostRequest
import org.companerodeescuela.shared.contracts.UpdateChannelPostRequest
import org.companerodeescuela.shared.contracts.UserRole

class ChannelService(
    private val repository: ChannelRepository,
    private val accessPolicy: ChannelAccessPolicy,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun channelsFor(userId: String, roles: Set<UserRole>) =
        accessPolicy.channelsFor(userId, roles)

    suspend fun postsFor(
        userId: String,
        roles: Set<UserRole>,
        channelId: String,
    ): List<ChannelPost> {
        accessPolicy.requireCanRead(userId, roles, channelId)
        return repository.listPosts(channelId).filter { it.state != ChannelPostState.DELETED }
    }

    suspend fun createPost(
        authorId: String,
        authorDisplayName: String,
        roles: Set<UserRole>,
        channelId: String,
        request: CreateChannelPostRequest,
    ): ChannelPost {
        accessPolicy.requireCanPublish(authorId, roles, channelId)
        validatePost(request.title, request.body, request.attachments)
        val now = clock.instant().epochSecond
        return repository.createPost(
            ChannelPost(
                id = UUID.randomUUID().toString(),
                channelId = channelId,
                authorId = authorId,
                authorDisplayName = authorDisplayName,
                type = request.type,
                title = request.title?.trim()?.takeIf(String::isNotEmpty),
                body = request.body?.trim()?.takeIf(String::isNotEmpty),
                attachments = request.attachments,
                allowedResponses = request.allowedResponses,
                pinned = request.pinned,
                createdAtEpochSeconds = now,
            ),
        )
    }

    suspend fun updatePost(
        actorId: String,
        roles: Set<UserRole>,
        channelId: String,
        postId: String,
        request: UpdateChannelPostRequest,
    ): ChannelPost {
        accessPolicy.requireCanPublish(actorId, roles, channelId)
        val current = requirePost(channelId, postId)
        if (current.authorId != actorId && roles.none(UserRole::isAdministrative)) {
            throw ApiException.Forbidden("Only the author or an administrator can edit this post")
        }
        val updated = current.copy(
            // null means "leave unchanged"; an explicit blank string means
            // "clear this optional field". Using ?: here made it impossible
            // for a teacher to remove an obsolete title/body from a post.
            title = if (request.title != null) {
                request.title.trim().takeIf(String::isNotEmpty)
            } else {
                current.title
            },
            body = if (request.body != null) {
                request.body.trim().takeIf(String::isNotEmpty)
            } else {
                current.body
            },
            attachments = request.attachments ?: current.attachments,
            allowedResponses = request.allowedResponses ?: current.allowedResponses,
            pinned = request.pinned ?: current.pinned,
            state = ChannelPostState.EDITED,
            editedAtEpochSeconds = clock.instant().epochSecond,
        )
        validatePost(updated.title, updated.body, updated.attachments)
        repository.replacePost(updated)
        return updated
    }

    suspend fun deletePost(
        actorId: String,
        roles: Set<UserRole>,
        channelId: String,
        postId: String,
    ): ChannelPost {
        accessPolicy.requireCanPublish(actorId, roles, channelId)
        val current = requirePost(channelId, postId)
        if (current.authorId != actorId && roles.none(UserRole::isAdministrative)) {
            throw ApiException.Forbidden("Only the author or an administrator can delete this post")
        }
        val deleted = current.copy(
            state = ChannelPostState.DELETED,
            editedAtEpochSeconds = clock.instant().epochSecond,
        )
        repository.replacePost(deleted)
        return deleted
    }

    suspend fun acknowledge(
        studentId: String,
        roles: Set<UserRole>,
        channelId: String,
        postId: String,
        request: ChannelAcknowledgementRequest,
    ): ChannelAcknowledgement {
        if (UserRole.STUDENT !in roles) {
            throw ApiException.Forbidden("Student role is required")
        }
        accessPolicy.requireCanRead(studentId, roles, channelId)
        val post = requirePost(channelId, postId)
        request.response?.let { response ->
            if (response !in post.allowedResponses) {
                throw ApiException.Validation("That response is not enabled for this post")
            }
        }

        val now = clock.instant().epochSecond
        return repository.upsertAcknowledgement(
            ChannelAcknowledgement(
                postId = postId,
                studentId = studentId,
                response = request.response,
                viewedAtEpochSeconds = now,
                respondedAtEpochSeconds = request.response?.let { now },
            ),
        )
    }

    suspend fun statsFor(
        actorId: String,
        roles: Set<UserRole>,
        channelId: String,
        postId: String,
    ): ChannelPostStats {
        accessPolicy.requireCanPublish(actorId, roles, channelId)
        requirePost(channelId, postId)
        val values = repository.acknowledgementsFor(postId)
        return ChannelPostStats(
            postId = postId,
            viewed = values.size,
            responded = values.count { it.response != null },
            byResponse = ChannelPresetResponse.entries.associateWith { response ->
                values.count { it.response == response }
            }.filterValues { it > 0 },
        )
    }

    private suspend fun requirePost(channelId: String, postId: String): ChannelPost {
        val post = repository.findPost(postId)
            ?: throw ApiException.NotFound("Channel post was not found")
        if (post.channelId != channelId || post.state == ChannelPostState.DELETED) {
            throw ApiException.NotFound("Channel post was not found")
        }
        return post
    }

    private fun validatePost(
        title: String?,
        body: String?,
        attachments: List<ChannelAttachment>,
    ) {
        if (title.isNullOrBlank() && body.isNullOrBlank() && attachments.isEmpty()) {
            throw ApiException.Validation("A post needs text or an attachment")
        }
        if ((title?.length ?: 0) > MAX_TITLE_LENGTH) {
            throw ApiException.Validation("Post title is too long")
        }
        if ((body?.length ?: 0) > MAX_BODY_LENGTH) {
            throw ApiException.Validation("Post body is too long")
        }
        if (attachments.size > MAX_ATTACHMENTS) {
            throw ApiException.Validation("Too many attachments")
        }
        attachments.forEach { attachment ->
            if (attachment.label.isBlank() || attachment.label.length > MAX_ATTACHMENT_LABEL_LENGTH) {
                throw ApiException.Validation("Attachment label is invalid")
            }
            if (!attachment.url.startsWith("https://")) {
                throw ApiException.Validation("Attachments must use HTTPS URLs")
            }
        }
    }

    private companion object {
        const val MAX_TITLE_LENGTH = 120
        const val MAX_BODY_LENGTH = 8_000
        const val MAX_ATTACHMENTS = 8
        const val MAX_ATTACHMENT_LABEL_LENGTH = 160
    }
}
