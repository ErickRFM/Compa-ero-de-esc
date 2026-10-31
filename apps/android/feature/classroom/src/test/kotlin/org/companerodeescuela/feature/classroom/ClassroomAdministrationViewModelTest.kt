@file:Suppress("ktlint:standard:no-wildcard-imports")

package org.companerodeescuela.feature.classroom

import androidx.lifecycle.viewModelScope
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.companerodeescuela.core.academic.AcademicRepository
import org.companerodeescuela.core.common.result.*
import org.companerodeescuela.core.database.*
import org.companerodeescuela.core.network.*
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.feature.admin.AdminRepository
import org.companerodeescuela.shared.contracts.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ClassroomAdministrationViewModelTest {
    private class Store : SessionTokenStore {
        val token = MutableStateFlow<String?>(jwt("admin"))

        override suspend fun readAccessToken() = token.value

        override suspend fun writeAccessToken(token: String) {
            this.token.value = token
        }

        override suspend fun clear() {
            token.value = null
        }

        override fun observeAccessToken(): Flow<String?> = token
    }

    private class Cache : AcademicSnapshotCache {
        override suspend fun read(ownerId: String): CachedAcademicLoad? = null

        override suspend fun write(value: AcademicLoadResponse) {}

        override suspend fun clear(ownerId: String) {}
    }

    private class Fixture {
        val store = Store()
        val clock = Clock.fixed(Instant.ofEpochSecond(1900000000), ZoneOffset.UTC)
        val actor = UserSummary("admin", "QA Admin", roles = setOf(UserRole.ADMIN), institutionId = "campus", emailVerified = true)
        val teacher =
            UserSummary("teacher", "Actual Teacher", roles = setOf(UserRole.TEACHER), institutionId = "campus", emailVerified = true)
        val student =
            UserSummary("student", "Actual Student", roles = setOf(UserRole.STUDENT), institutionId = "campus", emailVerified = true)
        val group =
            AcademicGroupSummary(
                "GROUP",
                "Actual Group",
                institutionId = "campus",
                periodId = "period",
                startsAtEpochSeconds = 1800000000,
                endsAtEpochSeconds = 2000000000,
                scopeRevision = 4,
            )
        val classroom =
            ClassroomSummary(
                "class",
                "Actual Class",
                groupId = "GROUP",
                groupName = "Actual Group",
                teacherId = "teacher",
                teacherDisplayName = "Actual Teacher",
                canManageEnrollment = true,
            )
        var scopeStatus = HttpStatusCode.OK
        var firstWrite = HttpStatusCode.OK
        var gate: CompletableDeferred<Unit>? = null
        val started = CompletableDeferred<Unit>()
        val proofs = mutableListOf<MfaStepUpRequest>()
        val bodies = mutableListOf<String>()
        val paths = mutableListOf<String>()
        val client =
            createApiClient(
                ApiEnvironment("https://example.test/", "test"),
                MockEngine { request ->
                    var status = HttpStatusCode.OK
                    val body =
                        when {
                            request.url.encodedPath == "/auth/me" -> Json.encodeToString(ApiResponse(actor))
                            request.url.encodedPath == "/admin/accounts/academic-directory" -> {
                                status = scopeStatus
                                Json.encodeToString(ApiResponse(listOf(teacher, student)))
                            }
                            request.url.encodedPath == "/institutions" ->
                                Json.encodeToString(
                                    ApiResponse(listOf(InstitutionSummary("campus", "Actual Campus"))),
                                )
                            request.url.encodedPath == "/auth/mfa/step-up" -> {
                                proofs += Json.decodeFromString<MfaStepUpRequest>((request.body as TextContent).text)
                                started.complete(Unit)
                                gate?.let { withContext(NonCancellable) { it.await() } }
                                Json.encodeToString(ApiResponse(StepUpProof("TEST-PROOF", 1900000200)))
                            }
                            request.method == HttpMethod.Get && request.url.encodedPath == "/academic/groups" ->
                                Json.encodeToString(
                                    ApiResponse(listOf(group)),
                                )
                            request.method == HttpMethod.Get && request.url.encodedPath == "/classrooms" ->
                                Json.encodeToString(
                                    ApiResponse(listOf(classroom)),
                                )
                            request.method == HttpMethod.Post -> {
                                paths += request.url.encodedPath
                                bodies += (request.body as TextContent).text
                                if (bodies.size == 1) status = firstWrite
                                when (request.url.encodedPath) {
                                    "/academic/groups" -> Json.encodeToString(ApiResponse(group))
                                    "/academic/groups/GROUP/members" ->
                                        Json.encodeToString(
                                            ApiResponse(AcademicGroupMembership("GROUP", "Actual Group", "student", 1)),
                                        )
                                    "/classrooms" -> Json.encodeToString(ApiResponse(classroom))
                                    "/classrooms/class/invites" ->
                                        Json.encodeToString(
                                            ApiResponse(ClassInvite("invite", "class", "TEST-CLASS-CODE", 1900001800, 100, 0)),
                                        )
                                    "/academic/manual-schedule" ->
                                        Json.encodeToString(
                                            ApiResponse(
                                                ManagedScheduleBlock(
                                                    "GROUP",
                                                    ScheduleBlock(
                                                        "block",
                                                        "MONDAY",
                                                        "07:00",
                                                        "08:00",
                                                        subjectName = "Actual Class",
                                                        provenance =
                                                            AcademicProvenance(
                                                                AcademicDataSource.ADMIN_MANUAL,
                                                                createdAtEpochSeconds = 1,
                                                                updatedAtEpochSeconds = 1,
                                                            ),
                                                    ),
                                                    institutionId = "campus",
                                                    groupScopeRevision = 4,
                                                    revision = 1,
                                                ),
                                            ),
                                        )
                                    else -> error("Unexpected POST")
                                }
                            }
                            request.method == HttpMethod.Delete -> "{}"
                            else -> error("Unexpected endpoint")
                        }
                    respond(if (status == HttpStatusCode.OK) body else "{}", status, headersOf(HttpHeaders.ContentType, "application/json"))
                },
            )
        val vm =
            ClassroomAdministrationViewModel(
                ClassroomRepository(client, store),
                AcademicGroupRepository(client, store),
                AdminRepository(client, store),
                AcademicRepository(client, store, Cache()),
                MfaRepository(client, store),
                clock,
            )

        suspend fun await(predicate: (ClassroomAdministrationState) -> Boolean) =
            withContext(Dispatchers.Default) {
                withTimeout(5000) { vm.state.first(predicate) }
            }

        suspend fun ready() {
            await { !it.loading }
            assertEquals(actor, vm.state.value.actor)
            assertEquals(listOf(group), vm.state.value.groups)
        }

        suspend fun run() {
            assertNotNull(vm.state.value.operationId)
            vm.authorize("test-password", "123456")
            await {
                !it.loading &&
                    !it.working
            }
            assertNull(vm.state.value.failure)
            assertTrue(vm.state.value.completed)
        }

        fun close() {
            gate?.complete(Unit)
            vm.viewModelScope.cancel()
            client.close()
        }
    }

    @Test fun `existing academic repositories send normalized scoped creation membership class and schedule with exact MFA digests`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val f = Fixture()
            try {
                f.ready()
                f.vm.prepareGroup(" new group ", "Actual New Group", "campus", "period", 1900000000, 2000000000)
                f.run()
                val group = Json.decodeFromString<CreateAcademicGroupRequest>(f.bodies[0])
                assertEquals("NEW-GROUP", group.id)
                assertEquals("campus", group.institutionId)
                assertEquals("TEST-PROOF", group.proof)
                assertEquals(PrivilegedCommandDigests.groupCreation("NEW-GROUP", group), f.proofs[0].payloadDigest)
                f.vm.prepareMember("GROUP", "student")
                f.run()
                val member = Json.decodeFromString<AssignAcademicGroupMemberRequest>(f.bodies[1])
                assertEquals(4, member.expectedScopeRevision)
                assertEquals(PrivilegedCommandDigests.groupMembership("GROUP", member), f.proofs[1].payloadDigest)
                f.vm.prepareClass("GROUP", "teacher", "Actual Class", "201")
                f.run()
                val assigned = Json.decodeFromString<CreateClassroomRequest>(f.bodies[2])
                assertEquals("Actual Teacher", assigned.teacherDisplayName)
                assertEquals(4, assigned.groupScopeRevision)
                assertEquals(PrivilegedCommandDigests.classroomAssignment("GROUP", assigned), f.proofs[2].payloadDigest)
                f.vm.prepareSchedule("class", "MONDAY", "07:00", "08:00")
                f.run()
                val scheduled = Json.decodeFromString<UpsertScheduleBlockRequest>(f.bodies[3])
                assertEquals("GROUP", scheduled.ownerId)
                assertEquals("class", scheduled.subjectId)
                assertEquals("teacher", scheduled.teacherId)
                assertEquals(4, scheduled.groupScopeRevision)
                assertEquals(PrivilegedCommandDigests.scheduleUpsert("GROUP", scheduled), f.proofs[3].payloadDigest)
                assertTrue(f.proofs.all { it.action == StepUpAction.MANAGE_ACADEMICS })
                assertFalse(
                    f.vm.state.value
                        .toString()
                        .contains("TEST-PROOF"),
                )
            } finally {
                f.close()
                Dispatchers.resetMain()
            }
        }

    @Test fun `foreign or unknown group teacher and student never become client selected academic authority`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val f = Fixture()
            try {
                f.ready()
                f.vm.prepareClass("FOREIGN", "teacher", "Class", "201")
                assertNull(f.vm.state.value.operationId)
                f.vm.prepareClass("GROUP", "unknown", "Class", "201")
                assertNull(f.vm.state.value.operationId)
                f.vm.prepareMember("GROUP", "teacher")
                assertNull(f.vm.state.value.operationId)
                assertTrue(f.proofs.isEmpty())
                assertTrue(f.bodies.isEmpty())
            } finally {
                f.close()
                Dispatchers.resetMain()
            }
        }

    @Test fun `ambiguous academic write retries same scoped operation without issuing a different proof`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val f = Fixture()
            f.firstWrite = HttpStatusCode.ServiceUnavailable
            try {
                f.ready()
                f.vm.prepareMember("GROUP", "student")
                f.vm.authorize("test-password", "123456")
                f.await {
                    !it.working &&
                        it.failure != null
                }
                f.vm.authorize("", "")
                f.await { !it.loading && !it.working && it.completed }
                assertEquals(2, f.bodies.size)
                assertEquals(f.bodies[0], f.bodies[1])
                assertEquals(1, f.proofs.size)
            } finally {
                f.close()
                Dispatchers.resetMain()
            }
        }

    @Test fun `lost administrative scope clears users classes groups and operation rather than showing cached grants`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val f = Fixture()
            try {
                f.ready()
                f.vm.prepareMember("GROUP", "student")
                f.scopeStatus = HttpStatusCode.Forbidden
                f.vm.refresh()
                f.await { !it.loading }
                assertNull(f.vm.state.value.actor)
                assertTrue(
                    f.vm.state.value.users
                        .isEmpty(),
                )
                assertTrue(
                    f.vm.state.value.groups
                        .isEmpty(),
                )
                assertTrue(
                    f.vm.state.value.classrooms
                        .isEmpty(),
                )
                assertNull(f.vm.state.value.operationId)
                assertNotNull(f.vm.state.value.failure)
            } finally {
                f.close()
                Dispatchers.resetMain()
            }
        }

    @Test fun `late old administrator proof cannot publish a schedule after owner replacement`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val f = Fixture()
            f.gate = CompletableDeferred()
            try {
                f.ready()
                f.vm.prepareSchedule("class", "MONDAY", "07:00", "08:00")
                f.vm.authorize("test-password", "123456")
                withContext(Dispatchers.Default) { withTimeout(5000) { f.started.await() } }
                f.store.token.value = jwt("replacement")
                runCurrent()
                f.gate!!.complete(Unit)
                f.await { !it.loading && !it.working }
                assertNull(f.vm.state.value.operationId)
                assertTrue(f.bodies.isEmpty())
                assertFalse(f.vm.state.value.completed)
            } finally {
                f.close()
                Dispatchers.resetMain()
            }
        }

    @Test fun `existing class invitation uses typed JSON and is cleared when administrator scope is lost`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val f = Fixture()
            try {
                f.ready()
                f.vm.generateInvite("class")
                f.await { !it.working }
                assertEquals(
                    "class",
                    f.vm.state.value.activeInvite
                        ?.classroomId,
                )
                assertEquals(CreateClassInviteRequest(), Json.decodeFromString<CreateClassInviteRequest>(f.bodies.single()))
                f.scopeStatus = HttpStatusCode.Forbidden
                f.vm.refresh()
                f.await { !it.loading }
                assertNull(f.vm.state.value.activeInvite)
            } finally {
                f.close()
                Dispatchers.resetMain()
            }
        }

    companion object {
        private fun jwt(id: String) =
            "e30." +
                Base64.getUrlEncoder().withoutPadding().encodeToString(
                    """{"sub":"$id","session_id":"session-$id","roles":["ADMIN"],"exp":4102444800}""".toByteArray(),
                ) +
                ".test"
    }
}
