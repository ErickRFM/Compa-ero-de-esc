package org.companerodeescuela.feature.channel

import kotlin.test.Test
import kotlin.test.assertEquals
import org.companerodeescuela.shared.contracts.ChannelAttachment
import org.companerodeescuela.shared.contracts.ChannelAttachmentType
import org.companerodeescuela.shared.contracts.ChannelPost
import org.companerodeescuela.shared.contracts.ChannelPostType

class ChannelFeedFilterTest {
    private fun post(id: String, type: ChannelPostType = ChannelPostType.ANNOUNCEMENT, withAttachment: Boolean = false) =
        ChannelPost(
            id = id,
            channelId = "class-1",
            authorId = "teacher-1",
            authorDisplayName = "Docente",
            type = type,
            createdAtEpochSeconds = 1234L,
            attachments = if (withAttachment) listOf(ChannelAttachment(ChannelAttachmentType.LINK, "Documento", "https://example.test/document")) else emptyList(),
        )

    @Test
    fun `publications preserve all original items and their order`() {
        val posts = listOf(post("a"), post("b", withAttachment = true), post("c", ChannelPostType.MATERIAL))
        assertEquals(listOf("a", "b", "c"), channelPostsForTab(posts, ChannelFeedTab.POSTS).map { it.id })
    }

    @Test
    fun `resources include both materials and notices with attachments`() {
        val posts = listOf(post("notice"), post("link", withAttachment = true), post("material", ChannelPostType.MATERIAL))
        assertEquals(listOf("link", "material"), channelPostsForTab(posts, ChannelFeedTab.RESOURCES).map { it.id })
    }

    @Test
    fun `no material remains an honest empty result`() {
        assertEquals(emptyList(), channelPostsForTab(listOf(post("notice")), ChannelFeedTab.RESOURCES))
    }
}
