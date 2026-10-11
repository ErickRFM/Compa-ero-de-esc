package org.companerodeescuela

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.companerodeescuela.core.attendance.AttendanceRemoteClient
import org.companerodeescuela.core.attendance.AttendanceSyncWorker
import org.companerodeescuela.core.database.AttendanceLocalStoreFactory
import org.companerodeescuela.core.database.CompaneroDatabase
import org.companerodeescuela.core.database.LocalAttendanceSyncState
import org.companerodeescuela.core.network.ApiEnvironment
import org.companerodeescuela.core.network.SessionRefreshCoordinator
import org.companerodeescuela.core.network.createApiClient
import org.companerodeescuela.core.security.SessionTokenStore
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AttendanceSyncWorkerTest {
    @Test
    fun anotherOwnersPendingRowDoesNotBlockCurrentOwnersDelivery() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, CompaneroDatabase::class.java).build()
        val store = AttendanceLocalStoreFactory.create(database)
        val now = System.currentTimeMillis() / 1000
        store.enqueue("a-other", "student-A", "session-A", now, "signed-A")
        store.enqueue("b-current", "student-B", "session-B", now, "signed-B")
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
            """{"sub":"student-B","session_id":"login-B","exp":4102444800}""".toByteArray(),
        )
        val tokens = object : SessionTokenStore {
            override suspend fun readAccessToken() = "e30.$payload.signature"
            override suspend fun writeAccessToken(token: String) = Unit
            override suspend fun clear() = Unit
        }
        val client = createApiClient(ApiEnvironment("https://example.test/", "test"), MockEngine { request ->
            assertEquals("/attendance/sessions/session-B/attempts", request.url.encodedPath)
            respond("""{"data":{"id":"session-B:student-B","operationId":"b-current","sessionId":"session-B","occurrenceId":"occurrence-B","studentId":"student-B","status":"review_required","reasonCode":"offline_network_qr_review","attemptedAtEpochSeconds":$now,"receivedAtEpochSeconds":$now}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            val remote = AttendanceRemoteClient(client, SessionRefreshCoordinator(client, tokens))
            val factory = object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                    AttendanceSyncWorker(appContext, workerParameters, store, remote)
            }
            val worker = TestListenableWorkerBuilder<AttendanceSyncWorker>(context).setWorkerFactory(factory).build()
            assertEquals(ListenableWorker.Result.success(), worker.doWork())
            assertEquals(LocalAttendanceSyncState.REVIEW_REQUIRED, store.findLocal("b-current")?.syncState)
            assertEquals(LocalAttendanceSyncState.PENDING, store.findLocal("a-other")?.syncState)
        } finally { client.close(); database.close() }
    }
}
