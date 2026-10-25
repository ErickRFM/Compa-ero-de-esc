package org.companerodeescuela.api.academic

import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.companerodeescuela.api.academic.groups.AcademicGroupMembershipRecord
import org.companerodeescuela.api.academic.groups.InMemoryAcademicGroupRepository
import org.companerodeescuela.api.attendance.AttendanceAccessPolicy
import org.companerodeescuela.api.attendance.TeacherCampusRosterService
import org.companerodeescuela.api.attendance.attendanceRoutes
import org.companerodeescuela.api.attendance.AttendanceAdministrationPolicy
import org.companerodeescuela.api.attendance.AttendanceEnrollmentResolver
import org.companerodeescuela.api.attendance.AttendanceReviewService
import org.companerodeescuela.api.attendance.AttendanceSessionService
import org.companerodeescuela.api.attendance.AttendanceStudentService
import org.companerodeescuela.api.attendance.InMemoryAttendanceRepository
import org.companerodeescuela.api.attendance.ProviderAttendanceOccurrenceResolver
import org.companerodeescuela.api.auth.AuthTokenService
import org.companerodeescuela.api.auth.InMemoryPlatformAccountRepository
import org.companerodeescuela.api.auth.InMemoryRefreshSessionRepository
import org.companerodeescuela.api.auth.PlatformSessionAuthority
import org.companerodeescuela.api.auth.RefreshSession
import org.companerodeescuela.api.auth.SessionIdentitySource
import org.companerodeescuela.api.classroom.ClassroomService
import org.companerodeescuela.api.classroom.InMemoryClassroomRepository
import org.companerodeescuela.api.classroom.NativeClassroom
import org.companerodeescuela.api.classroom.NativeClassroomMembership
import org.companerodeescuela.api.devices.InMemoryDeviceTokenRepository
import org.companerodeescuela.api.devices.InMemoryPushOutboxRepository
import org.companerodeescuela.api.devices.PushDeliveryResult
import org.companerodeescuela.api.devices.PushGateway
import org.companerodeescuela.api.devices.PushOutboxService
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.integrations.mock.MockAcademicProvider
import org.companerodeescuela.api.integrations.mock.MockIdentityProvider
import org.companerodeescuela.api.plugins.configurePlugins
import org.companerodeescuela.api.test.ScopedAcademicFixtures
import org.companerodeescuela.api.test.TestFixtures
import org.companerodeescuela.shared.contracts.AcademicDataSource
import org.companerodeescuela.shared.contracts.AttendanceAttemptRequest
import org.companerodeescuela.shared.contracts.BlockStatus
import org.companerodeescuela.shared.contracts.ClassroomStatus
import org.companerodeescuela.shared.contracts.CreateAttendanceSessionRequest
import org.companerodeescuela.shared.contracts.NetworkVerificationMethod
import org.companerodeescuela.shared.contracts.SchoolPresenceIdentitySource
import org.companerodeescuela.shared.contracts.SchoolPresenceResponse
import org.companerodeescuela.shared.contracts.SchoolPresenceStatus
import org.companerodeescuela.shared.contracts.AcademicLoadResponse
import org.companerodeescuela.shared.contracts.AcademicProvenance
import org.companerodeescuela.shared.contracts.AcademicWeekResponse
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.AttendanceIdentitySource
import org.companerodeescuela.shared.contracts.AttendanceSessionResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionStatus
import org.companerodeescuela.shared.contracts.ClassOccurrenceStatusContract
import org.companerodeescuela.shared.contracts.ClassroomMemberRole
import org.companerodeescuela.shared.contracts.ManagedScheduleBlock
import org.companerodeescuela.shared.contracts.ScheduleBlock
import org.companerodeescuela.shared.contracts.ScheduleSource
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class NativeAcademicProjectionTest {
    private val now = Instant.parse("2026-10-05T07:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val date = LocalDate.parse("2026-10-05")

    private inner class Fixture {
        val teacher = UserSummary("T-0001", "Native Teacher", roles = setOf(UserRole.TEACHER), institutionId = "campus")
        val student = UserSummary("2020-10455", "Native Student", roles = setOf(UserRole.STUDENT), institutionId = "campus")
        val groups = InMemoryAcademicGroupRepository()
        val classRepo = InMemoryClassroomRepository()
        val schedules = InMemoryAcademicScheduleOverrideRepository()
        lateinit var accounts: InMemoryPlatformAccountRepository
        lateinit var classes: ClassroomService
        lateinit var projection: NativeAcademicProjection

        suspend fun initialize() {
            accounts = ScopedAcademicFixtures.accounts(teacher, student)
            groups.create(ScopedAcademicFixtures.group("native-group", "101-A").copy(institutionId = "campus"))
            groups.assign(AcademicGroupMembershipRecord("native-group", student.id, now))
            classRepo.createClassroom(
                NativeClassroom(
                    "native-class",
                    "Native Mathematics",
                    null,
                    "Aula actual",
                    "native-group",
                    "101-A",
                    teacher.id,
                    teacher.displayName,
                    ClassroomStatus.ACTIVE,
                    now,
                    0,
                    0,
                ),
            )
            classRepo.addMembershipIfAbsent(NativeClassroomMembership("native-class", teacher.id, ClassroomMemberRole.TEACHER, now))
            classes = ClassroomService(classRepo, groups, clock, accounts = accounts)
            schedules.save(record())
            projection = NativeAcademicProjection(accounts, classes, groups, schedules, clock)
        }

        fun record(id: String = "native-block") =
            ManagedScheduleBlock(
                "native-group",
                ScheduleBlock(
                    id,
                    "MONDAY",
                    "07:00",
                    "07:50",
                    "native-class",
                    "Untrusted label",
                    teacher.id,
                    "Untrusted teacher",
                    "Actual room",
                    "native-group",
                    "Untrusted group",
                    AcademicProvenance(
                        AcademicDataSource.ADMIN_MANUAL,
                        createdAtEpochSeconds = now.epochSecond,
                        updatedAtEpochSeconds = now.epochSecond,
                    ),
                ),
                institutionId = "campus",
                groupScopeRevision = 0,
                revision = 1,
                operationId = "schedule",
                operationDigest = "digest",
            )
    }

    @Test fun `native teachers and students inherit only actual scoped class schedules with stable dated meetings`() =
        runTest {
            val f = Fixture()
            f.initialize()
            for (actor in listOf(f.teacher, f.student)) {
                val load = f.projection.load(actor)
                assertEquals(actor.displayName, load.student.displayName)
                assertEquals(listOf("native-class"), load.schedule.entries.map { it.courseId })
                assertEquals(
                    "Native Mathematics",
                    load.schedule.entries
                        .single()
                        .subjectName,
                )
                assertEquals(
                    ScheduleSource.MANUAL,
                    load.schedule.entries
                        .single()
                        .source,
                )
                val week = f.projection.week(actor, date)
                assertEquals(1, week.occurrences.size)
                assertEquals("native-class", week.occurrences.single().courseId)
                assertEquals("101-A", week.occurrences.single().groupName)
                assertEquals(week, f.projection.week(actor, date.plusDays(1)))
            }
            val occurrence =
                f.projection
                    .week(f.teacher, date)
                    .occurrences
                    .single()
            assertEquals(
                occurrence.id,
                f.projection
                    .teacherOccurrence(f.teacher, occurrence.id, date)
                    .id.value,
            )
            assertNotEquals(
                occurrence.id,
                f.projection
                    .week(f.teacher, date.plusWeeks(1))
                    .occurrences
                    .single()
                    .id,
            )
        }

    @Test fun `native projection excludes foreign legacy stale and private owner rows and retains cancelled occurrences`() =
        runTest {
            val f = Fixture()
            f.initialize()
            f.schedules.save(f.record("foreign").copy(institutionId = "other"))
            f.schedules.save(f.record("legacy").copy(institutionId = null, groupScopeRevision = -1))
            f.schedules.save(f.record("stale").copy(groupScopeRevision = 9))
            f.schedules.save(f.record("personal").copy(ownerId = f.teacher.id))
            val cancelled = f.record().let { it.copy(block = it.block.copy(status = BlockStatus.CANCELLED), revision = 2) }
            f.schedules.save(cancelled)
            assertTrue(
                f.projection
                    .load(f.student)
                    .schedule.entries
                    .isEmpty(),
            )
            assertEquals(
                listOf(ClassOccurrenceStatusContract.CANCELLED),
                f.projection
                    .week(f.student, date)
                    .occurrences
                    .map { it.status },
            )
            assertFailsWith<ApiException.Conflict> {
                f.projection.teacherOccurrence(
                    f.teacher,
                    f.projection
                        .week(f.student, date)
                        .occurrences
                        .single()
                        .id,
                    date,
                )
            }
        }

    @Test fun `native self academic reads cannot fall back to provider profile with matching person ID`() =
        runTest {
            val f = Fixture()
            f.initialize()
            val service = AcademicService(MockAcademicProvider(), f.schedules, f.groups, f.projection)
            val load = service.loadFor(f.student.id, f.student)
            assertEquals("Native Student", load.student.displayName)
            assertEquals(listOf("native-class"), load.schedule.entries.map { it.courseId })
        }

    @Test fun `native attendance enrollment never substitutes institutional enrollment with matching student ID`() =
        runTest {
            val f = Fixture()
            f.initialize()
            val resolver = AttendanceEnrollmentResolver(MockAcademicProvider(), f.classes)
            assertEquals(setOf("native-class" to "101-A"), resolver.courseGroupsFor(f.student.id, f.student))
            assertFailsWith<ApiException.Forbidden> { resolver.courseGroupsFor("different-student", f.student) }
        }

    @Test fun `native teacher cannot open institutional occurrence with matching teacher ID`() =
        runTest {
            val f = Fixture()
            f.initialize()
            val provider = MockAcademicProvider()
            val external = AcademicService(provider).scheduleWeekFor(f.teacher.id, date).occurrences.first()
            val repository = InMemoryAttendanceRepository()
            val service =
                AttendanceSessionService(
                    repository,
                    ProviderAttendanceOccurrenceResolver(provider),
                    clock = clock,
                    nativeProjection = f.projection,
                )
            assertFailsWith<ApiException.NotFound> {
                service.openSession(f.teacher.id, CreateAttendanceSessionRequest(external.id, date.toString()), f.teacher)
            }
            assertTrue(repository.findOpenSessions().isEmpty())
        }

    @Test fun `institutional teacher cannot use native group names as a campus identity mapping`() =
        runTest {
            val f = Fixture()
            f.initialize()
            val provider = MockAcademicProvider()
            val external = AcademicService(provider).scheduleWeekFor(f.teacher.id, date).occurrences.first()
            val presence =
                org.companerodeescuela.api.presence.SchoolPresenceService(
                    org.companerodeescuela.api.presence
                        .InMemorySchoolPresenceRepository(),
                    org.companerodeescuela.api.presence
                        .SchoolPresencePolicy("", setOf("campus"), emptySet()),
                )
            val service =
                TeacherCampusRosterService(
                    ProviderAttendanceOccurrenceResolver(provider),
                    f.groups,
                    presence,
                    InMemoryAttendanceRepository(),
                    f.projection,
                    f.accounts,
                )
            assertFailsWith<ApiException.DependencyUnavailable> { service.forTeacher(f.teacher.id, external.id, date.toString()) }
        }

    @Test fun `global superadmin can inspect actual native attendance without inventing an own institution`() =
        runTest {
            val f = Fixture()
            f.initialize()
            val global = UserSummary("global", "Global", roles = setOf(UserRole.SUPER_ADMIN))
            f.accounts.create(ScopedAcademicFixtures.account(global))
            AttendanceAdministrationPolicy(f.accounts, f.classes).requireAdministration(global)
        }

    @Test fun `native attendance opens actual meeting and student registration carries the same tenant and source`() =
        runTest {
            val f = Fixture()
            f.initialize()
            val provider = MockAcademicProvider()
            val repository = InMemoryAttendanceRepository()
            val occurrence =
                f.projection
                    .week(f.teacher, date)
                    .occurrences
                    .single()
            val sessions =
                AttendanceSessionService(
                    repository,
                    ProviderAttendanceOccurrenceResolver(provider),
                    clock = clock,
                    nativeProjection = f.projection,
                )
            val session = sessions.openSession(f.teacher.id, CreateAttendanceSessionRequest(occurrence.id, date.toString()), f.teacher)
            assertEquals(AttendanceIdentitySource.NATIVE, session.identitySource)
            assertEquals("campus", session.institutionId)
            assertEquals("native-group", session.groupId)
            assertEquals(0, session.groupScopeRevision)
            val students =
                AttendanceStudentService(
                    repository,
                    AttendanceEnrollmentResolver(provider, f.classes),
                    clock = clock,
                    nativeProjection = f.projection,
                )
            assertEquals(listOf(session), students.activeFor(f.student.id, f.student))
            val record = students.register(f.student.id, session.id, AttendanceAttemptRequest("attempt", now.epochSecond), f.student)
            assertEquals(session.id, record.sessionId)
            assertEquals(f.student.id, record.studentId)
            assertEquals(record, students.myClassRecord(f.student.id, session.id, f.student))
            assertFailsWith<ApiException.Forbidden> { students.myClassRecord(f.student.id, session.id) }
        }

    @Test fun `opening a native pass durably queues only the active class student without calling FCM inline`() =
        runTest {
            val f = Fixture()
            f.initialize()
            val repository = InMemoryAttendanceRepository()
            val studentToken = "fcm_test_token_student_123456"
            val devices =
                InMemoryDeviceTokenRepository().also {
                    it.register("NATIVE:${f.student.id}", studentToken, "android", null, "student-device")
                }
            val outbox = InMemoryPushOutboxRepository()
            var sends = 0
            val push =
                PushOutboxService(
                    devices,
                    outbox,
                    PushGateway { _, _, _ ->
                        sends++
                        PushDeliveryResult.ACCEPTED
                    },
                )
            val occurrence =
                f.projection
                    .week(f.teacher, date)
                    .occurrences
                    .single()
            val sessions =
                AttendanceSessionService(
                    repository,
                    ProviderAttendanceOccurrenceResolver(MockAcademicProvider()),
                    clock = clock,
                    nativeProjection = f.projection,
                    pushService = push,
                )
            val session = sessions.openSession(f.teacher.id, CreateAttendanceSessionRequest(occurrence.id, date.toString()), f.teacher)

            assertEquals(0, sends, "opening the pass must not call FCM synchronously")
            val queued = outbox.claim(now)
            assertNotNull(queued)
            assertEquals("NATIVE:${f.student.id}", queued.owner)
            assertEquals(mapOf("type" to "attendance_session_opened", "sessionId" to session.id), queued.data)
        }

    @Test fun `institutional student cannot read stamped native session even when course group and student IDs overlap`() =
        runTest {
            val f = Fixture()
            f.initialize()
            val repository = InMemoryAttendanceRepository()
            repository.createSession(
                AttendanceSessionResponse(
                    "native",
                    "native-meeting",
                    "C-9001",
                    "101-A",
                    date.toString(),
                    "07:00",
                    "07:50",
                    f.teacher.id,
                    now.epochSecond,
                    now.plusSeconds(600).epochSecond,
                    status = AttendanceSessionStatus.OPEN,
                    identitySource = AttendanceIdentitySource.NATIVE,
                    institutionId = "campus",
                    groupId = "native-group",
                    groupScopeRevision = 0,
                ),
            )
            val students =
                AttendanceStudentService(
                    repository,
                    AttendanceEnrollmentResolver(MockAcademicProvider(), f.classes),
                    clock = clock,
                    nativeProjection = f.projection,
                )
            assertTrue(students.activeFor(f.student.id).isEmpty())
            assertFailsWith<ApiException.Forbidden> { students.myClassRecord(f.student.id, "native") }
        }

    @Test fun `native school presence requirement cannot reuse old source student row`() =
        runTest {
            val f = Fixture()
            f.initialize()
            val repository =
                org.companerodeescuela.api.presence
                    .InMemorySchoolPresenceRepository()
            repository.save(
                SchoolPresenceResponse(
                    "legacy",
                    f.student.id,
                    now.epochSecond,
                    now.plusSeconds(600).epochSecond,
                    status = SchoolPresenceStatus.ACTIVE,
                    qrVerified = true,
                    networkVerified = false,
                    networkVerificationMethod = NetworkVerificationMethod.SSID,
                ),
            )
            val presence =
                org.companerodeescuela.api.presence.SchoolPresenceService(
                    repository,
                    org.companerodeescuela.api.presence
                        .SchoolPresencePolicy("", setOf("campus"), emptySet()),
                    clock = clock,
                    accounts = f.accounts,
                )
            assertFailsWith<ApiException.Forbidden> { presence.requireActive(f.student.id, f.student) }
            assertNotNull(repository.findById("legacy"))
        }

    @Test fun `native teacher campus roster uses actual group identities and native school days only`() =
        runTest {
            val f = Fixture()
            f.initialize()
            val presenceRepo =
                org.companerodeescuela.api.presence
                    .InMemorySchoolPresenceRepository()
            presenceRepo.save(
                SchoolPresenceResponse(
                    "native-day",
                    f.student.id,
                    now.epochSecond,
                    now.plusSeconds(600).epochSecond,
                    status = SchoolPresenceStatus.ACTIVE,
                    qrVerified = true,
                    networkVerified = false,
                    networkVerificationMethod = NetworkVerificationMethod.SSID,
                    institutionId = "campus",
                    identitySource = SchoolPresenceIdentitySource.NATIVE,
                    operationId = "day",
                ),
            )
            val presence =
                org.companerodeescuela.api.presence.SchoolPresenceService(
                    presenceRepo,
                    org.companerodeescuela.api.presence
                        .SchoolPresencePolicy("", setOf("campus"), emptySet()),
                    clock = clock,
                    accounts = f.accounts,
                )
            val roster =
                TeacherCampusRosterService(
                    ProviderAttendanceOccurrenceResolver(MockAcademicProvider()),
                    f.groups,
                    presence,
                    InMemoryAttendanceRepository(),
                    f.projection,
                    f.accounts,
                )
            val occurrence =
                f.projection
                    .week(f.teacher, date)
                    .occurrences
                    .single()
            val result = roster.forTeacher(f.teacher.id, occurrence.id, date.toString(), f.teacher)
            assertEquals(listOf(f.student.id), result.students.map { it.studentId })
            assertEquals(now.epochSecond, result.students.single().campusEntryAtEpochSeconds)
        }

    @Test fun `institutional academic load does not inherit native memberships or schedules with same ID`() =
        runTest {
            val f = Fixture()
            f.initialize()
            val load = AcademicService(MockAcademicProvider(), f.schedules, f.groups, f.projection).loadInstitutional(f.student.id)
            assertTrue(load.schedule.entries.none { it.source == ScheduleSource.MANUAL })
            assertNotEquals(f.student.displayName, load.student.displayName)
        }

    @Test fun `native and institutional academic HTTP sessions select their actual source and native attendance connects`() =
        testApplication {
            val f = Fixture()
            runBlocking { f.initialize() }
            val settings = TestFixtures.settings().copy(jwtSecret = CharArray(32) { 'q' })
            val refresh = InMemoryRefreshSessionRepository()
            val tokens = AuthTokenService(settings)
            val identity = MockIdentityProvider()

            suspend fun token(
                actor: UserSummary,
                source: SessionIdentitySource = SessionIdentitySource.NATIVE,
            ): String {
                val id = actor.id + source.name
                val time = Instant.now()
                refresh.create(RefreshSession(id, "test-hash", actor, time, time.plusSeconds(3600), identitySource = source))
                return tokens.issue(actor, id).value
            }
            val provider = MockAcademicProvider()
            val repository = InMemoryAttendanceRepository()
            val access =
                AttendanceAccessPolicy(
                    repository,
                    ProviderAttendanceOccurrenceResolver(provider),
                    AttendanceAdministrationPolicy(f.accounts, f.classes),
                )
            val sessions =
                AttendanceSessionService(
                    repository,
                    ProviderAttendanceOccurrenceResolver(provider),
                    access,
                    clock,
                    nativeProjection = f.projection,
                )
            val students =
                AttendanceStudentService(
                    repository,
                    AttendanceEnrollmentResolver(provider, f.classes),
                    clock = clock,
                    nativeProjection = f.projection,
                )
            application {
                configurePlugins(settings, refresh, PlatformSessionAuthority(f.accounts, identity))
                routing {
                    academicRoutes(
                        settings,
                        provider,
                        scheduleOverrides = f.schedules,
                        groupRepository = f.groups,
                        nativeProjection = f.projection,
                    )
                    attendanceRoutes(settings, sessions, students, AttendanceReviewService(repository, access, clock))
                }
            }
            val studentToken = token(f.student)
            val teacherToken = token(f.teacher)
            val load = client.get("/academic/load") { bearerAuth(studentToken) }
            assertEquals(HttpStatusCode.OK, load.status)
            assertEquals(
                "Native Student",
                Json
                    .decodeFromString<ApiResponse<AcademicLoadResponse>>(load.bodyAsText())
                    .data.student.displayName,
            )
            val external = client.get("/academic/load") { bearerAuth(token(f.student, SessionIdentitySource.INSTITUTIONAL)) }
            assertEquals(HttpStatusCode.OK, external.status)
            assertTrue(
                Json.decodeFromString<ApiResponse<AcademicLoadResponse>>(external.bodyAsText()).data.schedule.entries.none {
                    it.source ==
                        ScheduleSource.MANUAL
                },
            )
            val week = client.get("/academic/schedule/v2?weekOf=$date") { bearerAuth(teacherToken) }
            assertEquals(HttpStatusCode.OK, week.status)
            val occurrence =
                Json
                    .decodeFromString<ApiResponse<AcademicWeekResponse>>(week.bodyAsText())
                    .data.occurrences
                    .single()
            val opened =
                client.post("/attendance/sessions") {
                    bearerAuth(teacherToken)
                    contentType(ContentType.Application.Json)
                    setBody(Json.encodeToString(CreateAttendanceSessionRequest(occurrence.id, date.toString())))
                }
            assertEquals(HttpStatusCode.OK, opened.status)
            val session = Json.decodeFromString<ApiResponse<AttendanceSessionResponse>>(opened.bodyAsText()).data
            val active = client.get("/attendance/sessions/active") { bearerAuth(studentToken) }
            assertEquals(HttpStatusCode.OK, active.status)
            assertEquals(
                listOf(session.id),
                Json.decodeFromString<ApiResponse<List<AttendanceSessionResponse>>>(active.bodyAsText()).data.map {
                    it.id
                },
            )
        }

    @Test fun `native attendance authority requires persisted source tenant and group revision even with matching class`() =
        runTest {
            val f = Fixture()
            f.initialize()
            val policy = AttendanceAdministrationPolicy(f.accounts, f.classes)
            val base =
                AttendanceSessionResponse(
                    "old",
                    "old-occurrence",
                    "native-class",
                    "101-A",
                    date.toString(),
                    "07:00",
                    "07:50",
                    f.teacher.id,
                    now.epochSecond,
                    now.plusSeconds(600).epochSecond,
                    status = AttendanceSessionStatus.OPEN,
                )
            assertFailsWith<ApiException.Forbidden> { policy.requireResource(f.teacher, base, false) }
            assertFailsWith<ApiException.Forbidden> {
                policy.requireResource(
                    f.teacher,
                    base.copy(
                        identitySource = AttendanceIdentitySource.NATIVE,
                        institutionId = "other",
                        groupId = "native-group",
                        groupScopeRevision = 0,
                    ),
                    false,
                )
            }
            assertFailsWith<ApiException.Forbidden> {
                policy.requireResource(
                    f.teacher,
                    base.copy(
                        identitySource = AttendanceIdentitySource.NATIVE,
                        institutionId = "campus",
                        groupId = "native-group",
                        groupScopeRevision = 9,
                    ),
                    false,
                )
            }
        }

    @Test fun `native calendar never creates meetings outside the actual group period dates`() =
        runTest {
            val f = Fixture()
            f.initialize()
            assertTrue(
                f.projection
                    .week(f.teacher, LocalDate.parse("2101-01-03"))
                    .occurrences
                    .isEmpty(),
            )
        }

    @Test fun `institutional school presence cannot reuse a known native day with matching student ID`() =
        runTest {
            val f = Fixture()
            f.initialize()
            val repository =
                org.companerodeescuela.api.presence
                    .InMemorySchoolPresenceRepository()
            repository.save(
                SchoolPresenceResponse(
                    "native",
                    f.student.id,
                    now.epochSecond,
                    now.plusSeconds(600).epochSecond,
                    status = SchoolPresenceStatus.ACTIVE,
                    qrVerified = true,
                    networkVerified = false,
                    networkVerificationMethod = NetworkVerificationMethod.SSID,
                    institutionId = "campus",
                    identitySource = SchoolPresenceIdentitySource.NATIVE,
                    operationId = "day",
                ),
            )
            val presence =
                org.companerodeescuela.api.presence.SchoolPresenceService(
                    repository,
                    org.companerodeescuela.api.presence
                        .SchoolPresencePolicy("", setOf("campus"), emptySet()),
                    clock = clock,
                    accounts = f.accounts,
                )
            assertFailsWith<ApiException.Forbidden> { presence.requireActive(f.student.id) }
        }

    @Test fun `native teacher roster includes actually invited classroom students without guessing group memberships`() =
        runTest {
            val f = Fixture()
            f.initialize()
            val invited = UserSummary("invited", "Invited Student", roles = setOf(UserRole.STUDENT), institutionId = "campus")
            f.accounts.create(ScopedAcademicFixtures.account(invited))
            f.classRepo.addMembershipIfAbsent(NativeClassroomMembership("native-class", invited.id, ClassroomMemberRole.STUDENT, now))
            val presence =
                org.companerodeescuela.api.presence.SchoolPresenceService(
                    org.companerodeescuela.api.presence
                        .InMemorySchoolPresenceRepository(),
                    org.companerodeescuela.api.presence
                        .SchoolPresencePolicy("", setOf("campus"), emptySet()),
                    clock = clock,
                    accounts = f.accounts,
                )
            val roster =
                TeacherCampusRosterService(
                    ProviderAttendanceOccurrenceResolver(MockAcademicProvider()),
                    f.groups,
                    presence,
                    InMemoryAttendanceRepository(),
                    f.projection,
                    f.accounts,
                )
            val occurrence =
                f.projection
                    .week(f.teacher, date)
                    .occurrences
                    .single()
            assertEquals(
                setOf(f.student.id, invited.id),
                roster
                    .forTeacher(f.teacher.id, occurrence.id, date.toString(), f.teacher)
                    .students
                    .map {
                        it.studentId
                    }.toSet(),
            )
        }
}
