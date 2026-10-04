package org.companerodeescuela.api.channel

import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.client.model.Sorts.descending
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bson.Document
import org.companerodeescuela.shared.contracts.ChannelAcknowledgement
import org.companerodeescuela.shared.contracts.ChannelAttachment
import org.companerodeescuela.shared.contracts.ChannelAttachmentType
import org.companerodeescuela.shared.contracts.ChannelPost
import org.companerodeescuela.shared.contracts.ChannelPostState
import org.companerodeescuela.shared.contracts.ChannelPostType
import org.companerodeescuela.shared.contracts.ChannelPresetResponse

class MongoChannelRepository(database: MongoDatabase) : ChannelRepository {
    private val posts: MongoCollection<Document> = database.getCollection(POSTS_COLLECTION)
    private val acknowledgements: MongoCollection<Document> =
        database.getCollection(ACKS_COLLECTION)
    private val indexMutex = Mutex()

    @Volatile
    private var indexesReady = false

    override suspend fun listPosts(channelId: String): List<ChannelPost> {
        ensureIndexes()
        return posts.find(eq("channelId", channelId))
            .sort(descending("pinned", "createdAtEpochSeconds"))
            .toList()
            .map { it.toPost() }
    }

    override suspend fun findPost(postId: String): ChannelPost? {
        ensureIndexes()
        return posts.find(eq("_id", postId)).firstOrNull()?.toPost()
    }

    override suspend fun createPost(post: ChannelPost): ChannelPost {
        ensureIndexes()
        posts.insertOne(post.toDocument())
        return post
    }

    override suspend fun replacePost(post: ChannelPost) {
        ensureIndexes()
        posts.replaceOne(eq("_id", post.id), post.toDocument(), ReplaceOptions().upsert(false))
    }

    override suspend fun upsertAcknowledgement(
        value: ChannelAcknowledgement,
    ): ChannelAcknowledgement {
        ensureIndexes()
        acknowledgements.replaceOne(
            and(eq("postId", value.postId), eq("studentId", value.studentId)),
            value.toDocument(),
            ReplaceOptions().upsert(true),
        )
        return value
    }

    override suspend fun acknowledgementsFor(postId: String): List<ChannelAcknowledgement> {
        ensureIndexes()
        return acknowledgements.find(eq("postId", postId))
            .toList()
            .map { it.toAcknowledgement() }
    }

    private suspend fun ensureIndexes() {
        if (indexesReady) return
        indexMutex.withLock {
            if (indexesReady) return@withLock

            posts.createIndex(
                Indexes.compoundIndex(
                    Indexes.ascending("channelId"),
                    Indexes.descending("createdAtEpochSeconds"),
                ),
                IndexOptions().name("ix_channel_posts_feed"),
            )
            acknowledgements.createIndex(
                Indexes.compoundIndex(
                    Indexes.ascending("postId"),
                    Indexes.ascending("studentId"),
                ),
                IndexOptions().unique(true).name("uq_channel_ack_student"),
            )
            acknowledgements.createIndex(
                Indexes.ascending("postId"),
                IndexOptions().name("ix_channel_ack_post"),
            )
            indexesReady = true
        }
    }

    private fun ChannelPost.toDocument(): Document = Document()
        .append("_id", id)
        .append("channelId", channelId)
        .append("authorId", authorId)
        .append("authorDisplayName", authorDisplayName)
        .append("type", type.name)
        .append("title", title)
        .append("body", body)
        .append(
            "attachments",
            attachments.map { attachment ->
                Document()
                    .append("type", attachment.type.name)
                    .append("label", attachment.label)
                    .append("url", attachment.url)
            },
        )
        .append("allowedResponses", allowedResponses.map(ChannelPresetResponse::name))
        .append("pinned", pinned)
        .append("state", state.name)
        .append("createdAtEpochSeconds", createdAtEpochSeconds)
        .append("editedAtEpochSeconds", editedAtEpochSeconds)

    private fun Document.toPost(): ChannelPost =
        ChannelPost(
            id = getString("_id"),
            channelId = getString("channelId"),
            authorId = getString("authorId"),
            authorDisplayName = getString("authorDisplayName"),
            type = ChannelPostType.valueOf(getString("type")),
            title = getString("title"),
            body = getString("body"),
            attachments = getList("attachments", Document::class.java).orEmpty().map { attachment ->
                ChannelAttachment(
                    type = ChannelAttachmentType.valueOf(attachment.getString("type")),
                    label = attachment.getString("label"),
                    url = attachment.getString("url"),
                )
            },
            allowedResponses = getList("allowedResponses", String::class.java)
                .orEmpty()
                .map(ChannelPresetResponse::valueOf)
                .toSet(),
            pinned = getBoolean("pinned", false),
            state = ChannelPostState.valueOf(getString("state")),
            createdAtEpochSeconds = requireLong("createdAtEpochSeconds"),
            editedAtEpochSeconds = numberAsLong("editedAtEpochSeconds"),
        )

    private fun ChannelAcknowledgement.toDocument(): Document = Document()
        .append("postId", postId)
        .append("studentId", studentId)
        .append("response", response?.name)
        .append("viewedAtEpochSeconds", viewedAtEpochSeconds)
        .append("respondedAtEpochSeconds", respondedAtEpochSeconds)

    private fun Document.toAcknowledgement(): ChannelAcknowledgement =
        ChannelAcknowledgement(
            postId = getString("postId"),
            studentId = getString("studentId"),
            response = getString("response")?.let(ChannelPresetResponse::valueOf),
            viewedAtEpochSeconds = requireLong("viewedAtEpochSeconds"),
            respondedAtEpochSeconds = numberAsLong("respondedAtEpochSeconds"),
        )

    private fun Document.requireLong(name: String): Long =
        numberAsLong(name) ?: error("Missing numeric field: $name")

    private fun Document.numberAsLong(name: String): Long? =
        (get(name) as? Number)?.toLong()

    private companion object {
        const val POSTS_COLLECTION = "class_channel_posts"
        const val ACKS_COLLECTION = "class_channel_acknowledgements"
    }
}
