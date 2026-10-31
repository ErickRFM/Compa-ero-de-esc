package org.companerodeescuela.api.events

import kotlinx.coroutines.test.runTest
import org.companerodeescuela.api.academic.groups.AcademicGroupMembershipRecord
import org.companerodeescuela.api.academic.groups.InMemoryAcademicGroupRepository
import org.companerodeescuela.api.auth.InMemoryPlatformAccountRepository
import org.companerodeescuela.api.classroom.ClassroomService
import org.companerodeescuela.api.classroom.InMemoryClassroomRepository
import org.companerodeescuela.api.classroom.NativeClassroom
import org.companerodeescuela.api.classroom.NativeClassroomMembership
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.institutions.InMemoryInstitutionRepository
import org.companerodeescuela.api.test.QaClock
import org.companerodeescuela.api.test.QaMfaOperator
import org.companerodeescuela.api.test.ScopedAcademicFixtures
import org.companerodeescuela.shared.contracts.AcademicEvent
import org.companerodeescuela.shared.contracts.AcademicEventScope
import org.companerodeescuela.shared.contracts.AcademicEventSource
import org.companerodeescuela.shared.contracts.AcademicEventTarget
import org.companerodeescuela.shared.contracts.AcademicEventType
import org.companerodeescuela.shared.contracts.AcademicEventWriteRequest
import org.companerodeescuela.shared.contracts.AcademicOperationRequest
import org.companerodeescuela.shared.contracts.ClassroomMemberRole
import org.companerodeescuela.shared.contracts.ClassroomStatus
import org.companerodeescuela.shared.contracts.InstitutionSummary
import org.companerodeescuela.shared.contracts.PrivilegedCommandDigests
import org.companerodeescuela.shared.contracts.StepUpAction
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AcademicEventNativeScopeTest {
    private inner class Fixture {
        val clock = QaClock()
        val accounts = InMemoryPlatformAccountRepository()
        val groups = InMemoryAcademicGroupRepository()
        val classes = InMemoryClassroomRepository()
        val repository = InMemoryAcademicEventRepository()
        val admin = UserSummary("admin", "Admin", roles = setOf(UserRole.ADMIN), institutionId = "campus")
        val teacher = UserSummary("teacher", "Teacher", roles = setOf(UserRole.TEACHER), institutionId = "campus")
        val student = UserSummary("student", "Student", roles = setOf(UserRole.STUDENT), institutionId = "campus")
        val guest = UserSummary("guest", "Guest", roles = setOf(UserRole.WORKSHOP_PARTICIPANT))
        lateinit var qa: QaMfaOperator
        lateinit var service: AcademicEventService

        suspend fun initialize() {
            qa = QaMfaOperator.create(accounts, admin, clock)
            for (actor in listOf(teacher, student, guest)) accounts.create(ScopedAcademicFixtures.account(actor))
            groups.create(ScopedAcademicFixtures.group("6A").copy(institutionId = "campus"))
            groups.create(ScopedAcademicFixtures.group("6B").copy(institutionId = "other"))
            groups.assign(AcademicGroupMembershipRecord("6A", student.id, clock.now, groupScopeRevision = 0))
            classes.createClassroom(
                NativeClassroom(
                    "own-class",
                    "Own class",
                    null,
                    null,
                    "6A",
                    "6A",
                    teacher.id,
                    "Teacher",
                    ClassroomStatus.ACTIVE,
                    clock.now,
                    0,
                    0,
                ),
            )
            classes.createClassroom(
                NativeClassroom(
                    "foreign-class",
                    "Foreign",
                    null,
                    null,
                    "6B",
                    "6B",
                    "outside",
                    "Outside",
                    ClassroomStatus.ACTIVE,
                    clock.now,
                    0,
                    0,
                ),
            )
            classes.addMembershipIfAbsent(NativeClassroomMembership("own-class", teacher.id, ClassroomMemberRole.TEACHER, clock.now))
            service =
                AcademicEventService(
                    repository,
                    accounts,
                    groups,
                    ClassroomService(classes, groups, clock, accounts = accounts),
                    InMemoryInstitutionRepository(listOf(InstitutionSummary("campus", "Campus"), InstitutionSummary("other", "Other"))),
                    qa.mfa,
                    clock,
                )
        }

        fun event(
            scope: AcademicEventScope = AcademicEventScope.COURSE,
            target: String = "own-class",
        ) = AcademicEvent(
            "",
            AcademicEventType.ANNOUNCEMENT,
            AcademicEventSource.ADMIN,
            "forged-author",
            target = AcademicEventTarget(scope, target),
            title = "Class notice",
            createdAtEpochSeconds = 1,
            groupScopeRevision = 0,
        )

        fun command(
            op: String = "create",
            event: AcademicEvent = event(),
        ) = AcademicEventWriteRequest(op, 0, event)

        suspend fun signed(
            command: AcademicEventWriteRequest,
            id: String? = null,
        ) = command.copy(
            proof =
                qa.proof(
                    StepUpAction.MANAGE_ACADEMICS,
                    id ?: command.event.target.id,
                    command.operationId,
                    PrivilegedCommandDigests.eventWrite(id ?: command.event.target.id, command),
                ),
        )
    }

    @Test fun `teacher event writer derives real author and checks current assigned classroom`() =
        runTest {
            val f = Fixture()
            f.initialize()
            assertFailsWith<ApiException.Forbidden> {
                f.service.writeScoped(
                    f.teacher,
                    null,
                    f.command(event = f.event(target = "foreign-class")),
                )
            }
            assertFailsWith<ApiException.Forbidden> {
                f.service.writeScoped(f.teacher, null, f.command(event = f.event(AcademicEventScope.INSTITUTION, "campus")))
            }
            val created = f.service.writeScoped(f.teacher, null, f.command())
            assertEquals(f.teacher.id, created.sourceId)
            assertEquals(AcademicEventSource.TEACHER, created.source)
            assertEquals("campus", created.institutionId)
            assertEquals("Own class", created.subjectName)
            assertEquals(1, created.audit.size)
        }

    @Test fun `admin needs actual capability own tenant and command bound MFA`() =
        runTest {
            val f = Fixture()
            f.initialize()
            val command = f.command(event = f.event(AcademicEventScope.INSTITUTION, "campus"))
            assertFailsWith<ApiException.Forbidden> { f.service.writeScoped(f.admin, null, command) }
            assertFailsWith<ApiException.Forbidden> {
                f.service.writeScoped(f.admin, null, f.command(event = f.event(AcademicEventScope.INSTITUTION, "other")))
            }
            val signed = f.signed(command)
            assertFailsWith<ApiException.Forbidden> {
                f.service.writeScoped(
                    f.admin,
                    null,
                    signed.copy(event = signed.event.copy(title = "tampered")),
                )
            }
            val created = f.service.writeScoped(f.admin, null, signed)
            assertEquals("campus", created.institutionId)
            assertEquals(f.admin.id, created.sourceId)
        }

    @Test fun `event feed denies guests and filters foreign unmigrated and nonmember student scope`() =
        runTest {
            val f = Fixture()
            f.initialize()
            val created = f.service.writeScoped(f.teacher, null, f.command())
            f.repository.save(
                created.copy(
                    id = "foreign",
                    institutionId = "other",
                    target = AcademicEventTarget(AcademicEventScope.COURSE, "foreign-class"),
                ),
            )
            f.repository.save(created.copy(id = "legacy", institutionId = null))
            assertFailsWith<ApiException.Forbidden> { f.service.listScoped(f.guest) }
            assertEquals(
                listOf(created.id),
                f.service
                    .listScoped(f.student)
                    .events
                    .map { it.id },
            )
            assertFailsWith<ApiException.Forbidden> { f.service.getScoped(f.student, "foreign") }
        }

    @Test fun `event operations use deterministic retry and strict revision without replacing foreign ID`() =
        runTest {
            val f = Fixture()
            f.initialize()
            val command = f.command()
            val first = f.service.writeScoped(f.teacher, null, command)
            assertEquals(first, f.service.writeScoped(f.teacher, null, command))
            assertEquals(1, f.repository.listEvents().size)
            assertFailsWith<ApiException.Conflict> {
                f.service.writeScoped(f.teacher, null, command.copy(event = command.event.copy(title = "different")))
            }
            assertFailsWith<ApiException.Validation> {
                f.service.writeScoped(f.teacher, null, f.command("collision", f.event().copy(id = "foreign-ID")))
            }
            val update = AcademicEventWriteRequest("update", first.revision, f.event().copy(title = "Updated"))
            val changed = f.service.writeScoped(f.teacher, first.id, update)
            assertEquals(first.revision + 1, changed.revision)
            assertEquals(2, changed.audit.size)
            assertFailsWith<ApiException.Conflict> { f.service.writeScoped(f.teacher, first.id, update.copy(operationId = "stale")) }
        }

    @Test fun `cancellation retains event audit and cannot be applied outside writer scope`() =
        runTest {
            val f = Fixture()
            f.initialize()
            val first = f.service.writeScoped(f.teacher, null, f.command())
            assertFailsWith<ApiException.Forbidden> {
                f.service.cancelScoped(f.student, first.id, AcademicOperationRequest("cancel", first.revision, ""))
            }
            assertTrue(f.service.cancelScoped(f.teacher, first.id, AcademicOperationRequest("cancel", first.revision, "")))
            val stored = assertNotNull(f.repository.findById(first.id))
            assertTrue(stored.cancelled)
            assertEquals(2, stored.audit.size)
            assertTrue(
                f.service
                    .listScoped(f.student)
                    .events
                    .isEmpty(),
            )
            assertTrue(f.service.cancelScoped(f.teacher, first.id, AcademicOperationRequest("cancel", first.revision, "")))
        }

    @Test fun `principal can read institution-scoped events`() =
        runTest {
            val f = Fixture()
            f.initialize()
            // Arrange: publish an institution-scoped event as admin, then verify PRINCIPAL can read it.
            val command = f.command(event = f.event(AcademicEventScope.INSTITUTION, "campus"))
            val signed = f.signed(command)
            val created = f.service.writeScoped(f.admin, null, signed)
            // PRINCIPAL belongs to the same institution; reading must succeed.
            val principal = UserSummary("principal1", "Principal", roles = setOf(UserRole.PRINCIPAL), institutionId = "campus")
            f.accounts.create(ScopedAcademicFixtures.account(principal))
            val feed = f.service.listScoped(principal)
            assertTrue(feed.events.any { it.id == created.id }, "PRINCIPAL must see institution-scoped events of their institution")
            // PRINCIPAL from a different institution must NOT see the event.
            val foreignPrincipal =
                UserSummary("principal2", "Foreign Principal", roles = setOf(UserRole.PRINCIPAL), institutionId = "other")
            f.accounts.create(ScopedAcademicFixtures.account(foreignPrincipal))
            val foreignFeed = f.service.listScoped(foreignPrincipal)
            assertTrue(foreignFeed.events.none { it.id == created.id }, "PRINCIPAL from another institution must NOT see foreign events")
        }

    @Test fun `coordinator can read group-scoped events for their institution`() =
        runTest {
            val f = Fixture()
            f.initialize()
            // Arrange: publish a group-scoped event as teacher (group 6A belongs to "campus").
            val groupEvent = f.event(AcademicEventScope.GROUP, "6A")
            val created = f.service.writeScoped(f.teacher, null, f.command(event = groupEvent))
            // COORDINATOR of the same institution must be able to read the group event.
            val coordinator = UserSummary("coord1", "Coordinator", roles = setOf(UserRole.COORDINATOR), institutionId = "campus")
            f.accounts.create(ScopedAcademicFixtures.account(coordinator))
            val feed = f.service.listScoped(coordinator)
            assertTrue(feed.events.any { it.id == created.id }, "COORDINATOR must see group-scoped events of their institution")
            // COORDINATOR from another institution must NOT see the event.
            val foreignCoordinator =
                UserSummary("coord2", "Foreign Coordinator", roles = setOf(UserRole.COORDINATOR), institutionId = "other")
            f.accounts.create(ScopedAcademicFixtures.account(foreignCoordinator))
            val foreignFeed = f.service.listScoped(foreignCoordinator)
            assertTrue(
                foreignFeed.events.none { it.id == created.id },
                "COORDINATOR from another institution must NOT see foreign group events",
            )
        }

    @Test fun `principal cannot write events (requires MANAGE_ACADEMICS)`() =
        runTest {
            val f = Fixture()
            f.initialize()
            // PRINCIPAL is not an admin, so write must be denied for institution-scoped events...
            val principal = UserSummary("principal3", "Principal", roles = setOf(UserRole.PRINCIPAL), institutionId = "campus")
            f.accounts.create(ScopedAcademicFixtures.account(principal))
            assertFailsWith<ApiException.Forbidden>("PRINCIPAL must not publish institution-scoped events") {
                f.service.writeScoped(principal, null, f.command(event = f.event(AcademicEventScope.INSTITUTION, "campus")))
            }
            // ...and also for course-scoped events (teacher assignment is required).
            assertFailsWith<ApiException.Forbidden>("PRINCIPAL must not publish course-scoped events without teacher assignment") {
                f.service.writeScoped(principal, null, f.command(event = f.event(AcademicEventScope.COURSE, "own-class")))
            }
        }
}
