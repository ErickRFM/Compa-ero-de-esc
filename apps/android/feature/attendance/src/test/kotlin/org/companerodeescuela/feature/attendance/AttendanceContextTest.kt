package org.companerodeescuela.feature.attendance

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import java.lang.reflect.Proxy
import java.time.LocalDate
import java.util.Base64
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
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
import org.companerodeescuela.core.attendance.AttendanceQrPackStore
import org.companerodeescuela.shared.contracts.AttendanceSessionResponse
import org.companerodeescuela.shared.contracts.AttendanceQrResponse
import org.companerodeescuela.shared.contracts.ClassOccurrenceContract
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
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
    fun `offline switching and recreated view model reject another class cached pass`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val now = System.currentTimeMillis() / 1000
        val date = LocalDate.now().toString()
        var offline = false
        val cache = object : AttendanceQrPackStore {
            var session: AttendanceSessionResponse? = null
            var occurrence: ClassOccurrenceContract? = null
            var slots = emptyList<AttendanceQrResponse>()
            override fun read(ownerId: String, sessionId: String) = slots
            override fun save(ownerId: String, sessionId: String, slots: List<AttendanceQrResponse>) { this.slots = slots }
            override fun rememberSession(ownerId: String, session: AttendanceSessionResponse) { this.session = session }
            override fun restoreSession(ownerId: String) = session
            override fun rememberOccurrence(ownerId: String, occurrence: ClassOccurrenceContract) { this.occurrence = occurrence }
            override fun restoreOccurrence(ownerId: String) = occurrence
            override fun clearSession(ownerId: String) { session = null; slots = emptyList() }
        }
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
            """{"sub":"T1","roles":["TEACHER"],"exp":4102444800,"session_id":"login-T1"}""".toByteArray())
        val tokens = object : SessionTokenStore {
            override suspend fun readAccessToken() = "e30.$payload.signature"
            override suspend fun writeAccessToken(token: String) = Unit
            override suspend fun clear() = Unit
        }
        val client = createApiClient(ApiEnvironment("https://example.test/", "test"), MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request ->
                if (offline) respondError(HttpStatusCode.ServiceUnavailable) else {
                    val data = when {
                        request.url.encodedPath == "/academic/schedule/v2" -> """{"ownerId":"T1","weekStartsOn":"$date","weekEndsOn":"$date","occurrences":[${occurrence("A", date)}]}"""
                        request.url.encodedPath.endsWith("/qr-pack") -> """[{"token":"signed","issuedAtEpochSeconds":$now,"expiresAtEpochSeconds":${now + 25},"rotateAfterSeconds":15}]"""
                        request.url.encodedPath.endsWith("/qr") -> """{"token":"signed","issuedAtEpochSeconds":$now,"expiresAtEpochSeconds":${now + 25},"rotateAfterSeconds":15}"""
                        request.url.encodedPath == "/attendance/sessions/mine" -> """[{"id":"pass-A","occurrenceId":"A","courseId":"course-A","groupName":"A","occurrenceDate":"$date","scheduledStartsAt":"09:00","scheduledEndsAt":"10:00","openedBy":"T1","openedAtEpochSeconds":$now,"closesAtEpochSeconds":${now + 60},"status":"open"}]"""
                        else -> "[]"
                    }
                    respond("{\"data\":$data}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }
        }))
        val local = Proxy.newProxyInstance(AttendanceLocalStore::class.java.classLoader, arrayOf(AttendanceLocalStore::class.java)) { _, method, _ ->
            if (method.name == "observe") flowOf(emptyList<LocalAttendanceRecord>()) else error(method.name)
        } as AttendanceLocalStore
        val models = mutableListOf<AttendanceViewModel>()
        fun newVm() = AttendanceViewModel(AttendanceRepository(local, object : AttendanceSyncEnqueuer { override fun schedule() = Unit },
            AttendanceRemoteClient(client, SessionRefreshCoordinator(client, tokens)), offlineQrStore = cache, sessionTokenStore = tokens)).also(models::add)
        try {
            val vm = newVm()
            vm.setClassroomContext(TeacherClassContext("class-A", "Materia A", "A"))
            vm.selectMode(AttendanceMode.TEACHER)
            runCurrent()
            assertEquals("pass-A", vm.state.value.teacherSession?.id)
            assertTrue(cache.slots.isNotEmpty())
            offline = true
            val restoredA = newVm()
            restoredA.setClassroomContext(TeacherClassContext("class-A", "Materia A", "A"))
            restoredA.selectMode(AttendanceMode.TEACHER)
            runCurrent()
            assertEquals("pass-A", restoredA.state.value.teacherSession?.id)
            assertEquals("signed", restoredA.state.value.qr?.token)
            vm.setClassroomContext(TeacherClassContext("class-B", "Materia B", "B"))
            vm.selectMode(AttendanceMode.TEACHER)
            runCurrent()
            assertEquals(null, vm.state.value.teacherSession)
            assertEquals(null, vm.state.value.qr)
            val recreated = newVm()
            recreated.setClassroomContext(TeacherClassContext("class-B", "Materia B", "B"))
            recreated.selectMode(AttendanceMode.TEACHER)
            runCurrent()
            assertEquals(null, recreated.state.value.teacherSession)
        } finally { models.forEach { it.viewModelScope.cancel() }; runCurrent(); client.close(); Dispatchers.resetMain() }
    }

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
