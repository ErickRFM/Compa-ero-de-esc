package org.companerodeescuela.feature.attendance

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import java.lang.reflect.Proxy
import java.time.LocalDate
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.companerodeescuela.core.attendance.AttendanceRemoteClient
import org.companerodeescuela.core.attendance.AttendanceRepository
import org.companerodeescuela.core.attendance.AttendanceSyncEnqueuer
import org.companerodeescuela.core.database.AttendanceLocalStore
import org.companerodeescuela.core.database.LocalAttendanceRecord
import org.companerodeescuela.core.network.ApiEnvironment
import org.companerodeescuela.core.network.SessionRefreshCoordinator
import org.companerodeescuela.core.network.createApiClient
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.shared.contracts.TeacherClassContext

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceContextTest {
    @Test
    fun `changing class rejects delayed previous campus roster`() = verifyIsolation(false)

    @Test
    fun `dual role student response cannot replace teacher class context`() = verifyIsolation(true)

    private fun verifyIsolation(dualRole: Boolean) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val releaseA = CompletableDeferred<Unit>()
        val startedA = CompletableDeferred<Unit>()
        var firstWeek = true
        val date = LocalDate.now().toString()
        val client = createApiClient(ApiEnvironment("https://example.test/", "test"), MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request ->
            val path = request.url.encodedPath
            val data = when {
                path == "/academic/schedule/v2" -> {
                    if (dualRole && firstWeek) {
                        firstWeek = false
                        startedA.complete(Unit)
                        releaseA.await()
                    }
                    """{"ownerId":"T1","weekStartsOn":"$date","weekEndsOn":"$date","occurrences":[${occurrence("A", date)},${occurrence("B", date)}]}"""
                }
                path.endsWith("/campus-roster") -> {
                    val id = if (path.contains("/A/")) "A" else "B"
                    if (id == "A") { startedA.complete(Unit); releaseA.await() }
                    """{"occurrenceId":"$id","groupName":"$id","students":[]}"""
                }
                else -> "[]"
            }
            respond("{\"data\":$data}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }))
        val roles = if (dualRole) "\"TEACHER\",\"STUDENT\"" else "\"TEACHER\""
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"sub":"T1","roles":[$roles],"exp":4102444800}""".toByteArray())
        val tokens = object : SessionTokenStore {
            override suspend fun readAccessToken() = "e30.$payload.signature"
            override suspend fun writeAccessToken(token: String) = Unit
            override suspend fun clear() = Unit
        }
        val local = Proxy.newProxyInstance(AttendanceLocalStore::class.java.classLoader, arrayOf(AttendanceLocalStore::class.java)) { _, method, _ ->
            when (method.name) {
                "observe" -> flowOf(emptyList<LocalAttendanceRecord>())
                else -> error("Unexpected local-store call: ${method.name}")
            }
        } as AttendanceLocalStore
        try {
            val remote = AttendanceRemoteClient(client, SessionRefreshCoordinator(client, tokens))
            val vm = AttendanceViewModel(AttendanceRepository(local, object : AttendanceSyncEnqueuer { override fun schedule() = Unit }, remote))
            if (!dualRole) {
                vm.setClassroomContext(TeacherClassContext("class-A", "Materia A", "A"))
                vm.selectMode(AttendanceMode.TEACHER)
            }
            runCurrent()
            assertTrue(startedA.isCompleted)
            vm.setClassroomContext(TeacherClassContext("class-B", "Materia B", "B"))
            vm.selectMode(AttendanceMode.TEACHER)
            runCurrent()
            assertEquals("B", vm.state.value.campusRoster?.occurrenceId)
            assertEquals(listOf("B"), vm.state.value.occurrences.map { it.id })
            assertEquals(AttendanceMode.TEACHER, vm.state.value.mode)
            releaseA.complete(Unit)
            runCurrent()
            assertEquals("B", vm.state.value.campusRoster?.occurrenceId)
        } finally { client.close(); Dispatchers.resetMain() }
    }

    private fun occurrence(id: String, date: String): String = """{"id":"$id","patternId":null,"courseId":"course-$id","groupName":"$id","subjectCode":"$id","subjectName":"Materia $id","teacherName":"Docente","date":"$date","startsAt":"09:00","endsAt":"10:00","status":"scheduled"}"""
}
