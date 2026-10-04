package org.companerodeescuela.api.channel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.integrations.mock.MockAcademicProvider
import org.companerodeescuela.shared.contracts.ChannelAcknowledgementRequest
import org.companerodeescuela.shared.contracts.ChannelPresetResponse
import org.companerodeescuela.shared.contracts.CreateChannelPostRequest
import org.companerodeescuela.shared.contracts.UserRole

class ChannelServiceTest {
    private val service = ChannelService(
        repository = InMemoryChannelRepository(),
        accessPolicy = ChannelAccessPolicy(MockAcademicProvider()),
    )

    @Test
    fun `student cannot publish free-form content`() = runTest {
        assertFailsWith<ApiException.Forbidden> {
            service.createPost(
                authorId = "2020-10455",
                authorDisplayName = "Ana López Hernández",
                roles = setOf(UserRole.STUDENT),
                channelId = "C-9001",
                request = CreateChannelPostRequest(body = "No debería publicarse"),
            )
        }
    }

    @Test
    fun `assigned teacher can publish and student can only use enabled response`() = runTest {
        val post = service.createPost(
            authorId = "T-0001",
            authorDisplayName = "Mtra. Elena Ríos Salgado",
            roles = setOf(UserRole.TEACHER),
            channelId = "C-9001",
            request = CreateChannelPostRequest(
                body = "Mañana trabajaremos en laboratorio.",
                allowedResponses = setOf(ChannelPresetResponse.ACKNOWLEDGED),
            ),
        )

        val acknowledgement = service.acknowledge(
            studentId = "2020-10455",
            roles = setOf(UserRole.STUDENT),
            channelId = "C-9001",
            postId = post.id,
            request = ChannelAcknowledgementRequest(ChannelPresetResponse.ACKNOWLEDGED),
        )

        assertEquals(ChannelPresetResponse.ACKNOWLEDGED, acknowledgement.response)
        assertFailsWith<ApiException.Validation> {
            service.acknowledge(
                studentId = "2020-10455",
                roles = setOf(UserRole.STUDENT),
                channelId = "C-9001",
                postId = post.id,
                request = ChannelAcknowledgementRequest(ChannelPresetResponse.WILL_ATTEND),
            )
        }
    }

    @Test
    fun `teacher stats aggregate closed student responses`() = runTest {
        val post = service.createPost(
            authorId = "T-0001",
            authorDisplayName = "Mtra. Elena Ríos Salgado",
            roles = setOf(UserRole.TEACHER),
            channelId = "C-9001",
            request = CreateChannelPostRequest(body = "Confirma que viste este aviso."),
        )
        service.acknowledge(
            studentId = "2020-10455",
            roles = setOf(UserRole.STUDENT),
            channelId = "C-9001",
            postId = post.id,
            request = ChannelAcknowledgementRequest(ChannelPresetResponse.ACKNOWLEDGED),
        )

        val stats = service.statsFor(
            actorId = "T-0001",
            roles = setOf(UserRole.TEACHER),
            channelId = "C-9001",
            postId = post.id,
        )

        assertEquals(1, stats.viewed)
        assertEquals(1, stats.responded)
        assertEquals(1, stats.byResponse[ChannelPresetResponse.ACKNOWLEDGED])
    }
}
