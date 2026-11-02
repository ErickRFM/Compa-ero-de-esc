package org.companerodeescuela.feature.channel

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.companerodeescuela.core.network.ApiEnvironment
import org.companerodeescuela.core.network.createApiClient
import org.companerodeescuela.core.security.SessionTokenStore
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelNotificationSelectionTest {
    @Test
    fun `cold task notification selects authorized classroom B instead of first classroom A`() =
        runTest {
            Fixture(this).use { f ->
                f.viewModel.requestChannel("B")
                assertNull(f.viewModel.state.value.resolvedNotificationChannelId)
                runCurrent()
                assertEquals("B", f.viewModel.state.value.selectedChannelId)
                assertEquals("B", f.viewModel.state.value.resolvedNotificationChannelId)
                assertTrue("/channels/B/posts" in f.paths)
                assertFalse("/channels/A/posts" in f.paths)
            }
        }

    @Test
    fun `warm notification revalidates channels and clears prior classroom draft`() =
        runTest {
            Fixture(this).use { f ->
                runCurrent()
                assertEquals("A", f.viewModel.state.value.selectedChannelId)
                f.viewModel.updateDraftBody("Only for A")
                f.viewModel.updateDraftTaskTitle("Task only for A")
                f.viewModel.updateDraftTaskInstructions("Private instructions for A")
                f.viewModel.requestChannel("B")
                runCurrent()
                assertEquals(2, f.paths.count { it == "/channels" })
                assertEquals("B", f.viewModel.state.value.selectedChannelId)
                assertEquals("", f.viewModel.state.value.draftBody)
                assertEquals("", f.viewModel.state.value.draftTaskTitle)
                assertEquals("", f.viewModel.state.value.draftTaskInstructions)
                f.viewModel.consumeRequestedChannel()
                assertNull(f.viewModel.state.value.resolvedNotificationChannelId)
            }
        }

    @Test
    fun `revoked or forged classroom never selects fallback or fetches private posts`() =
        runTest {
            Fixture(this).use { f ->
                runCurrent()
                f.viewModel.selectChannel("B")
                runCurrent()
                f.ids = listOf("A")
                f.paths.clear()
                f.viewModel.requestChannel("B")
                runCurrent()
                assertNull(f.viewModel.state.value.selectedChannelId)
                assertEquals("B", f.viewModel.state.value.resolvedNotificationChannelId)
                assertTrue(
                    f.viewModel.state.value.errorMessage
                        ?.contains("autorizado") == true,
                )
                assertEquals(listOf("/channels"), f.paths)
                f.viewModel.requestChannel("another-institution")
                runCurrent()
                assertNull(f.viewModel.state.value.selectedChannelId)
                assertFalse(f.paths.any { it.contains("another-institution") })
            }
        }

    private class Fixture(
        scope: TestScope,
    ) : AutoCloseable {
        var ids = listOf("A", "B")
        val paths = mutableListOf<String>()
        private val dispatcher = StandardTestDispatcher(scope.testScheduler)
        private val client =
            createApiClient(
                ApiEnvironment("https://example.test/", "test"),
                MockEngine(
                    MockEngineConfig().apply {
                        dispatcher = this@Fixture.dispatcher
                        addHandler { request ->
                            paths += request.url.encodedPath
                            respond(
                                if (request.url.encodedPath == "/channels") {
                                    "{\"data\":[${ids.joinToString(",", transform = ::channel)}]}"
                                } else {
                                    "{\"data\":[]}"
                                },
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }
                    },
                ),
            )
        val viewModel: ChannelViewModel

        init {
            Dispatchers.setMain(dispatcher)
            viewModel = ChannelViewModel(ChannelRepository(client, tokenStore()))
        }

        override fun close() {
            client.close()
            Dispatchers.resetMain()
        }

        private fun channel(id: String): String =
            """{"id":"$id","courseId":"$id","subjectCode":"MAT","subjectName":"Math","groupName":"3A","term":"2026","teacherId":"T1","teacherDisplayName":"Teacher","canPublish":false}"""

        private fun tokenStore(): SessionTokenStore {
            val payload =
                Base64.getUrlEncoder().withoutPadding().encodeToString(
                    """{"sub":"student","roles":["STUDENT"],"exp":4102444800}""".toByteArray(),
                )
            return object : SessionTokenStore {
                override suspend fun readAccessToken() = "e30.$payload.signature"

                override suspend fun writeAccessToken(token: String) = Unit

                override suspend fun clear() = Unit
            }
        }
    }
}
