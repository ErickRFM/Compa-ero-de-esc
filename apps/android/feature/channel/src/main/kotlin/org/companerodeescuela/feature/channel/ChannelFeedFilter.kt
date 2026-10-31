package org.companerodeescuela.feature.channel

import org.companerodeescuela.shared.contracts.ChannelPost
import org.companerodeescuela.shared.contracts.ChannelPostType

/** A class channel does not contain a free-form student chat. */
internal enum class ChannelFeedTab { POSTS, RESOURCES }

/** Explicit resource filter; regular publications remain visible in the primary feed. */
internal fun channelPostsForTab(
    posts: List<ChannelPost>,
    tab: ChannelFeedTab,
): List<ChannelPost> =
    when (tab) {
        ChannelFeedTab.POSTS -> posts
        ChannelFeedTab.RESOURCES ->
            posts.filter {
                it.type == ChannelPostType.MATERIAL || it.attachments.isNotEmpty()
            }
    }
