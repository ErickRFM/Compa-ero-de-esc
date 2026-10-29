package org.companerodeescuela.feature.attendance

import androidx.lifecycle.viewModelScope
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.companerodeescuela.core.attendance.AttendanceRemoteClient
import org.companerodeescuela.core.attendance.AttendanceRepository
import org.companerodeescuela.core.attendance.AttendanceSyncEnqueuer
import org.companerodeescuela.core.attendance.SchoolNetworkEvidenceProvider
import org.companerodeescuela.core.database.AttendanceLocalStore
import org.companerodeescuela.core.database.LocalAttendanceRecord
import org.companerodeescuela.core.network.ApiEnvironment
import org.companerodeescuela.core.network.SessionRefreshCoordinator
import org.companerodeescuela.core.network.createApiClient
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.shared.contracts.SchoolNetworkEvidence
import java.lang.reflect.Proxy
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceStudentHistoryTest {
    @Test fun `slow history does not delay live sessions or requery during polling`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val fixture = Fixture(testScheduler)
            val release = CompletableDeferred<Unit>()
            try {
                fixture.activeSession = true
                fixture.historyGate = release
                runCurrent()
                assertTrue(fixture.historyStarted.isCompleted)
                assertEquals(
                    Fixture.SESSION,
                    fixture.model.state.value.activeSessions
                        .single()
                        .id,
                )
                assertFalse(fixture.model.state.value.loading)
                advanceTimeBy(20_001)
                runCurrent()
                assertEquals(1, fixture.paths.count { it == "/attendance/records/mine" })
                release.complete(Unit)
                runCurrent()
                assertEquals(
                    "closed-record",
                    fixture.model.state.value.studentHistory
                        .single()
                        .id,
                )
            } finally {
                release.complete(Unit)
                fixture.close()
                Dispatchers.resetMain()
            }
        }

    @Test fun `late old account history cannot replace the new account history`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val fixture = Fixture(testScheduler)
            val release = CompletableDeferred<Unit>()
            try {
                fixture.historyGate = release
                runCurrent()
                assertTrue(fixture.historyStarted.isCompleted)
                fixture.owner = "other-student"
                fixture.historyGate = null
                fixture.model.selectMode(AttendanceMode.STUDENT)
                runCurrent()
                assertEquals("other-student", fixture.model.state.value.userId)
                assertEquals(
                    "other-record",
                    fixture.model.state.value.studentHistory
                        .single()
                        .id,
                )
                release.complete(Unit)
                runCurrent()
                assertEquals(
                    "other-record",
                    fixture.model.state.value.studentHistory
                        .single()
                        .id,
                )
            } finally {
                release.complete(Unit)
                fixture.close()
                Dispatchers.resetMain()
            }
        }

    @Test fun `late student history cannot repopulate teacher mode`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val fixture = Fixture(testScheduler)
            val release = CompletableDeferred<Unit>()
            try {
                fixture.historyGate = release
                runCurrent()
                assertTrue(fixture.historyStarted.isCompleted)
                fixture.model.selectMode(AttendanceMode.TEACHER)
                runCurrent()
                release.complete(Unit)
                runCurrent()
                assertEquals(AttendanceMode.TEACHER, fixture.model.state.value.mode)
                assertTrue(
                    fixture.model.state.value.studentHistory
                        .isEmpty(),
                )
            } finally {
                release.complete(Unit)
                fixture.close()
                Dispatchers.resetMain()
            }
        }

    @Test fun `notification selects session without submitting attendance`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val fixture = Fixture(testScheduler)
            try {
                fixture.activeSession = true
                runCurrent()
                fixture.model.openRequestedSession(Fixture.SESSION)
                runCurrent()
                assertEquals(Fixture.SESSION, fixture.model.state.value.requestedStudentSessionId)
                assertTrue(fixture.paths.none { it.endsWith("/confirm") })
                assertEquals(
                    Fixture.SESSION,
                    fixture.model.state.value.activeSessions
                        .single()
                        .id,
                )
                fixture.model.confirmClassCall(Fixture.SESSION)
                runCurrent()
                assertEquals(1, fixture.paths.count { it.endsWith("/confirm") })
            } finally {
                fixture.close()
                Dispatchers.resetMain()
            }
        }

    @Test fun `closed class history survives recreated view model and transient failure`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val fixture = Fixture(testScheduler)
            try {
                runCurrent()
                assertTrue(
                    fixture.model.state.value.activeSessions
                        .isEmpty(),
                )
                assertEquals(
                    "closed-record",
                    fixture.model.state.value.studentHistory
                        .single()
                        .id,
                )
                fixture.recreate()
                runCurrent()
                assertEquals(
                    "closed-record",
                    fixture.model.state.value.studentHistory
                        .single()
                        .id,
                )
                fixture.historyStatus = HttpStatusCode.ServiceUnavailable
                fixture.model.refresh()
                runCurrent()
                assertEquals(
                    "closed-record",
                    fixture.model.state.value.studentHistory
                        .single()
                        .id,
                )
                assertNotNull(fixture.model.state.value.studentHistoryError)
            } finally {
                fixture.close()
                Dispatchers.resetMain()
            }
        }

    @Test fun `history authorization denial clears previously visible records`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val fixture = Fixture(testScheduler)
            try {
                runCurrent()
                fixture.historyStatus = HttpStatusCode.Forbidden
                fixture.model.refresh()
                runCurrent()
                assertTrue(
                    fixture.model.state.value.studentHistory
                        .isEmpty(),
                )
                assertNotNull(fixture.model.state.value.studentHistoryError)
            } finally {
                fixture.close()
                Dispatchers.resetMain()
            }
        }

    private class Fixture(
        scheduler: TestCoroutineScheduler,
    ) {
        var historyStatus = HttpStatusCode.OK
        var activeSession = false
        var owner = "student"
        var historyGate: CompletableDeferred<Unit>? = null
        val historyStarted = CompletableDeferred<Unit>()
        val paths = mutableListOf<String>()
        private val tokens =
            object : SessionTokenStore {
                override suspend fun readAccessToken(): String {
                    val claims =
                        Base64.getUrlEncoder().withoutPadding().encodeToString(
                            """
                            {
                                "sub":"$owner",
                                "roles":["STUDENT","TEACHER"],
                                "exp":4102444800,
                                "session_id":"login-$owner"
                            }
                            """.trimIndent().toByteArray(),
                        )
                    return "e30.$claims.signature"
                }

                override suspend fun writeAccessToken(token: String) = Unit

                override suspend fun clear() = Unit
            }
        private val client =
            createApiClient(
                ApiEnvironment("https://example.test/", "test"),
                MockEngine(
                    MockEngineConfig().apply {
                        dispatcher = StandardTestDispatcher(scheduler)
                        addHandler { request ->
                            val path = request.url.encodedPath
                            paths += path
                            val responseOwner = owner
                            val recordId = if (responseOwner == "student") "closed-record" else "other-record"
                            val gate = historyGate
                            if (path == "/attendance/records/mine" && gate != null) {
                                historyStarted.complete(Unit)
                                withContext(NonCancellable) { gate.await() }
                            }
                            val status = if (path == "/attendance/records/mine") historyStatus else HttpStatusCode.OK
                            val data =
                                when (path) {
                                    "/attendance/records/mine" ->
                                        """
                                        [{
                                            "id":"$recordId",
                                            "operationId":"operation",
                                            "sessionId":"closed-session",
                                            "occurrenceId":"occurrence",
                                            "studentId":"$responseOwner",
                                            "status":"review_required",
                                            "reasonCode":"class_call_confirmed",
                                            "attemptedAtEpochSeconds":1,
                                            "receivedAtEpochSeconds":1
                                        }]
                                        """.trimIndent()
                                    "/academic/schedule/v2" ->
                                        """
                                        {
                                            "ownerId":"student",
                                            "weekStartsOn":"2026-10-26",
                                            "weekEndsOn":"2026-11-01",
                                            "occurrences":[]
                                        }
                                        """.trimIndent()
                                    "/presence/school-day" -> "null"
                                    "/attendance/sessions/active" ->
                                        if (activeSession) {
                                            """
                                            [{
                                                "id":"$SESSION",
                                                "occurrenceId":"occurrence",
                                                "courseId":"course",
                                                "groupName":"9A",
                                                "occurrenceDate":"2026-10-28",
                                                "scheduledStartsAt":"08:00",
                                                "scheduledEndsAt":"09:00",
                                                "openedBy":"teacher",
                                                "openedAtEpochSeconds":1,
                                                "closesAtEpochSeconds":4102444800,
                                                "status":"open"
                                            }]
                                            """.trimIndent()
                                        } else {
                                            "[]"
                                        }
                                    "/attendance/sessions/$SESSION/mine" -> "null"
                                    "/attendance/sessions/$SESSION/confirm" ->
                                        """
                                        {
                                            "id":"new-record",
                                            "operationId":"operation",
                                            "sessionId":"$SESSION",
                                            "occurrenceId":"occurrence",
                                            "studentId":"student",
                                            "status":"review_required",
                                            "reasonCode":"class_call_confirmed",
                                            "attemptedAtEpochSeconds":1,
                                            "receivedAtEpochSeconds":1
                                        }
                                        """.trimIndent()
                                    else -> "[]"
                                }
                            respond(
                                if (status == HttpStatusCode.OK) "{\"data\":$data}" else "{}",
                                status,
                                headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }
                    },
                ),
            )
        private val local =
            Proxy.newProxyInstance(
                AttendanceLocalStore::class.java.classLoader,
                arrayOf(AttendanceLocalStore::class.java),
            ) { _, method, _ ->
                if (method.name == "observe") flowOf(emptyList<LocalAttendanceRecord>()) else error(method.name)
            } as AttendanceLocalStore
        private val repository =
            AttendanceRepository(
                local,
                object : AttendanceSyncEnqueuer {
                    override fun schedule() = Unit
                },
                AttendanceRemoteClient(client, SessionRefreshCoordinator(client, tokens)),
                sessionTokenStore = tokens,
                networkEvidenceProvider = SchoolNetworkEvidenceProvider { SchoolNetworkEvidence(ssid = "QA-school") },
            )
        var model = AttendanceViewModel(repository)
            private set

        fun recreate() {
            model.viewModelScope.cancel()
            model = AttendanceViewModel(repository)
        }

        fun close() {
            model.viewModelScope.cancel()
            client.close()
        }

        companion object {
            const val SESSION = "11111111-1111-4111-8111-111111111111"
        }
    }
}
