package org.companerodeescuela.feature.channel

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.companerodeescuela.core.network.ApiEnvironment
import org.companerodeescuela.core.network.createApiClient
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.shared.contracts.TeacherClassContext

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelContextTest {
    @Test
    fun `native class resolves by authorized identity despite room group label`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val client = createApiClient(ApiEnvironment("https://example.test/", "test"), MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request ->
            respond(if (request.url.encodedPath == "/channels") "{\"data\":[${channel("native", "201")},${channel("institutional", "3A")}] }"
                else "{\"data\":[]}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }))
        try {
            val vm = ChannelViewModel(ChannelRepository(client, tokenStore()))
            vm.requestClassroom(TeacherClassContext("native", "Matemáticas", "3A"))
            runCurrent()
            assertEquals("native", vm.state.value.selectedChannelId)
        } finally { client.close(); Dispatchers.resetMain() }
    }

    @Test
    fun `assignment removal clears draft before selecting a different channel`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var removed = false
        val client = createApiClient(ApiEnvironment("https://example.test/", "test"), MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request ->
            respond(if (request.url.encodedPath == "/channels") {
                "{\"data\":[${if (removed) channel("B", "4B") else channel("A", "3A")}] }"
            } else "{\"data\":[]}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }))
        try {
            val vm = ChannelViewModel(ChannelRepository(client, tokenStore()))
            runCurrent()
            vm.updateDraftBody("Solo para A")
            vm.updateDraftResourceUrl("https://example.test/A.pdf")
            removed = true
            vm.refreshChannels()
            runCurrent()
            assertEquals("B", vm.state.value.selectedChannelId)
            assertEquals("", vm.state.value.draftBody)
            assertEquals("", vm.state.value.draftResourceUrl)
        } finally { client.close(); Dispatchers.resetMain() }
    }

    private fun channel(id: String, group: String): String = """{"id":"$id","courseId":"$id","subjectCode":"MAT","subjectName":"Matemáticas","groupName":"$group","term":"2026","teacherId":"T1","teacherDisplayName":"Docente","canPublish":true}"""

    private fun tokenStore(): SessionTokenStore {
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"sub":"T1","roles":["TEACHER"],"exp":4102444800}""".toByteArray())
        return object : SessionTokenStore {
            override suspend fun readAccessToken() = "e30.$payload.signature"
            override suspend fun writeAccessToken(token: String) = Unit
            override suspend fun clear() = Unit
        }
    }
}
