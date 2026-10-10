package org.companerodeescuela.api.classroom

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.companerodeescuela.api.academic.groups.AcademicGroupMembershipRecord
import org.companerodeescuela.api.academic.groups.AcademicGroupRecord
import org.companerodeescuela.api.academic.groups.InMemoryAcademicGroupRepository
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.channel.ChannelAccessPolicy
import org.companerodeescuela.api.integrations.mock.MockAcademicProvider
import org.companerodeescuela.shared.contracts.CreateClassInviteRequest
import org.companerodeescuela.shared.contracts.CreateClassroomRequest
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary

class ClassroomServiceTest {
    @Test
    fun adminCreatesClassAndAssignedTeacherReceivesMembership() = runTest {
        val repository = InMemoryClassroomRepository()
        val service = service(repository, BASE_TIME)

        val classroom = create(service,request())

        assertEquals("9A", classroom.groupName)
        assertEquals(TEACHER.id, classroom.teacherId)
        assertTrue(classroom.canManageEnrollment)

        val teacherClasses = service.listFor(TEACHER)
        assertEquals(1, teacherClasses.size)
        assertEquals(classroom.id, teacherClasses.single().id)
        assertTrue(teacherClasses.single().canManage)
        assertFalse(teacherClasses.single().canManageEnrollment)
        val nativeChannel = ChannelAccessPolicy(MockAcademicProvider(), service,accounts=operatorFor(service).let { serviceAccounts[service] })
            .channelsFor(TEACHER.id, TEACHER.roles,TEACHER,org.companerodeescuela.api.auth.SessionIdentitySource.NATIVE).single { it.id == classroom.id }
        assertEquals("9A", nativeChannel.groupName)
        assertEquals(classroom.id, nativeChannel.courseId)
        assertTrue(nativeChannel.canPublish)
    }

    @Test
    fun teacherCannotCreateClassroom() = runTest {
        val service = service(InMemoryClassroomRepository(), BASE_TIME)

        assertFailsWith<ApiException.Forbidden> {
            service.create(TEACHER, request())
        }
    }

    @Test
    fun pendingTeacherCannotCreateClassroom() = runTest {
        val service = service(InMemoryClassroomRepository(), BASE_TIME)

        assertFailsWith<ApiException.Forbidden> {
            service.create(PENDING_TEACHER, request())
        }
    }

    @Test
    fun teacherCannotGenerateEnrollmentCode() = runTest {
        val service = service(InMemoryClassroomRepository(), BASE_TIME)
        val classroom = create(service,request())

        assertFailsWith<ApiException.Forbidden> {
            service.createInvite(
                TEACHER,
                classroom.id,
                CreateClassInviteRequest(ttlMinutes = 30, maxUses = 10),
            )
        }
    }

    @Test
    fun adminEnrollmentCodeLetsStudentJoinAssignedClass() = runTest {
        val repository = InMemoryClassroomRepository()
        val service = service(repository, BASE_TIME)
        val classroom = create(service,request())
        val invite = service.createInvite(
            ADMIN,
            classroom.id,
            CreateClassInviteRequest(ttlMinutes = 30, maxUses = 10),
        )

        assertTrue(invite.code.matches(Regex("[A-Z2-9]{4}-[A-Z2-9]{4}")))
        val joined = service.join(STUDENT, invite.code)

        assertEquals(classroom.id, joined.classroom.id)
        assertEquals(STUDENT.id, joined.membership.userId)
        assertEquals(1, service.listFor(STUDENT).size)
    }

    @Test
    fun expiredEnrollmentCodeIsRejected() = runTest {
        val repository = InMemoryClassroomRepository()
        val classroom = create(service(repository, BASE_TIME),request(name = "Bases de datos"))
        val invite = service(repository, BASE_TIME).createInvite(
            ADMIN,
            classroom.id,
            CreateClassInviteRequest(ttlMinutes = 30),
        )

        assertFailsWith<ApiException.Conflict> {
            service(repository, BASE_TIME.plusSeconds(31 * 60)).join(STUDENT, invite.code)
        }
    }

    @Test
    fun revokedEnrollmentCodeIsRejected() = runTest {
        val repository = InMemoryClassroomRepository()
        val service = service(repository, BASE_TIME)
        val classroom = create(service,request(name = "Redes"))
        val invite = service.createInvite(ADMIN, classroom.id, CreateClassInviteRequest())
        service.revokeInvite(ADMIN, classroom.id, invite.id)

        assertFailsWith<ApiException.Conflict> {
            service.join(STUDENT, invite.code)
        }
    }

    @Test
    fun joiningSameClassroomIsIdempotent() = runTest {
        val repository = InMemoryClassroomRepository()
        val service = service(repository, BASE_TIME)
        val classroom = create(service,request(name = "Ingeniería de software"))
        val invite = service.createInvite(
            ADMIN,
            classroom.id,
            CreateClassInviteRequest(maxUses = 1),
        )

        val first = service.join(STUDENT, invite.code)
        val second = service.join(STUDENT, invite.code)

        assertEquals(first.membership, second.membership)
    }


    @Test
    fun studentInGroupAutomaticallySeesAssignedClasses() = runTest {
        val classrooms = InMemoryClassroomRepository()
        val groups = InMemoryAcademicGroupRepository()
        groups.create(
            AcademicGroupRecord(
                id = "group-9a",
                name = "9A",
                active = true,
                createdAt = BASE_TIME,institutionId="qa-campus",periodId="qa-period",startsAt=Instant.EPOCH,endsAt=Instant.parse("2100-01-01T00:00:00Z"),
            ),
        )
        groups.assign(
            AcademicGroupMembershipRecord(
                groupId = "group-9a",
                userId = STUDENT.id,
                joinedAt = BASE_TIME,
            ),
        )
        val service=service(classrooms,BASE_TIME,groups)
        val classroom = create(service,request())

        val visible = service.listFor(STUDENT)

        assertEquals(listOf(classroom.id), visible.map { it.id })
        assertEquals(classroom.id, service.requireCanRead(STUDENT, classroom.id).id)
    }

    private fun request(name: String = "Programación móvil") = CreateClassroomRequest(
        name = name,
        room = "Laboratorio A",
        groupId = "group-9a",
        groupName = "9A",
        teacherId = TEACHER.id,
        teacherDisplayName = TEACHER.displayName,
    )

    private val serviceOperators=java.util.IdentityHashMap<ClassroomService,org.companerodeescuela.api.test.QaMfaOperator>()
    private val serviceAccounts=java.util.IdentityHashMap<ClassroomService,org.companerodeescuela.api.auth.PlatformAccountRepository>()
    private fun operatorFor(service:ClassroomService)=serviceOperators[service] ?: error("Actual MFA fixture is required")
    private suspend fun create(service:ClassroomService,request:CreateClassroomRequest):org.companerodeescuela.shared.contracts.ClassroomSummary {
        val operation=java.util.UUID.randomUUID().toString()
        var command=request.copy(operationId=operation)
        command=command.copy(proof=operatorFor(service).proof(org.companerodeescuela.shared.contracts.StepUpAction.MANAGE_ACADEMICS,request.groupId,operation,org.companerodeescuela.shared.contracts.PrivilegedCommandDigests.classroomAssignment(request.groupId,command)))
        return service.create(ADMIN,command)
    }
    private suspend fun service(repository:ClassroomRepository,instant:Instant,groups:InMemoryAcademicGroupRepository=InMemoryAcademicGroupRepository()):ClassroomService {
        val clock=org.companerodeescuela.api.test.QaClock(instant)
        val accounts=org.companerodeescuela.api.auth.InMemoryPlatformAccountRepository()
        val operator=org.companerodeescuela.api.test.QaMfaOperator.create(accounts,ADMIN,clock)
        for(actor in listOf(TEACHER,STUDENT,PENDING_TEACHER)) accounts.create(org.companerodeescuela.api.test.ScopedAcademicFixtures.account(actor))
        if(groups.find("group-9a")==null) groups.create(org.companerodeescuela.api.test.ScopedAcademicFixtures.group("group-9a","9A"))
        val service=ClassroomService(repository,groups,clock,accounts=accounts,mfa=operator.mfa)
        serviceOperators[service]=operator;serviceAccounts[service]=accounts
        return service
    }

    private companion object {
        val BASE_TIME: Instant = Instant.parse("2026-10-06T12:00:00Z")

        val ADMIN = UserSummary(
            id = "admin-1",
            displayName = "Control escolar",
            roles = setOf(UserRole.ADMIN),institutionId="qa-campus",
        )
        val TEACHER = UserSummary(
            id = "teacher-1",
            displayName = "Mtra. Elena",
            roles = setOf(UserRole.TEACHER),institutionId="qa-campus",
        )
        val PENDING_TEACHER = UserSummary(
            id = "teacher-pending",
            displayName = "Docente pendiente",
            roles = setOf(UserRole.TEACHER_PENDING),institutionId="qa-campus",
        )
        val STUDENT = UserSummary(
            id = "student-1",
            displayName = "Ana",
            roles = setOf(UserRole.STUDENT),institutionId="qa-campus",
        )
    }
}
