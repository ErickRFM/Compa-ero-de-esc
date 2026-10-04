package org.companerodeescuela.api.channel

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.companerodeescuela.shared.contracts.ChannelAcknowledgement
import org.companerodeescuela.shared.contracts.ChannelPost

interface ChannelRepository {
    suspend fun listPosts(channelId: String): List<ChannelPost>
    suspend fun findPost(postId: String): ChannelPost?
    suspend fun createPost(post: ChannelPost): ChannelPost
    suspend fun replacePost(post: ChannelPost)
    suspend fun upsertAcknowledgement(value: ChannelAcknowledgement): ChannelAcknowledgement
    suspend fun acknowledgementsFor(postId: String): List<ChannelAcknowledgement>
}

class InMemoryChannelRepository : ChannelRepository {
    private val mutex = Mutex()
    private val posts = linkedMapOf<String, ChannelPost>()
    private val acknowledgements = linkedMapOf<Pair<String, String>, ChannelAcknowledgement>()

    override suspend fun listPosts(channelId: String): List<ChannelPost> =
        mutex.withLock {
            posts.values
                .filter { it.channelId == channelId }
                .sortedWith(
                    compareByDescending<ChannelPost> { it.pinned }
                        .thenByDescending { it.createdAtEpochSeconds },
                )
        }

    override suspend fun findPost(postId: String): ChannelPost? =
        mutex.withLock { posts[postId] }

    override suspend fun createPost(post: ChannelPost): ChannelPost =
        mutex.withLock {
            require(posts[post.id] == null) { "Duplicate channel post id" }
            posts[post.id] = post
            post
        }

    override suspend fun replacePost(post: ChannelPost) {
        mutex.withLock { posts[post.id] = post }
    }

    override suspend fun upsertAcknowledgement(
        value: ChannelAcknowledgement,
    ): ChannelAcknowledgement = mutex.withLock {
        acknowledgements[value.postId to value.studentId] = value
        value
    }

    override suspend fun acknowledgementsFor(postId: String): List<ChannelAcknowledgement> =
        mutex.withLock {
            acknowledgements.values.filter { it.postId == postId }
        }
}
