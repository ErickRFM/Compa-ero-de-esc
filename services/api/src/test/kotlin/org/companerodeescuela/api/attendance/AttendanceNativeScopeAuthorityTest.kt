package org.companerodeescuela.api.attendance

import java.time.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import org.companerodeescuela.api.auth.*
import org.companerodeescuela.api.academic.groups.*
import org.companerodeescuela.api.classroom.*
import org.companerodeescuela.api.integrations.mock.MockAcademicProvider
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.test.ScopedAcademicFixtures
import org.companerodeescuela.shared.contracts.*

class AttendanceNativeScopeAuthorityTest {
    private val now=Instant.parse("2026-10-10T07:00:00Z")
    private inner class Fixture {
        val actor=UserSummary("admin","Admin",roles=setOf(UserRole.ADMIN),institutionId="campus")
        val teacher=UserSummary("teacher","Teacher",roles=setOf(UserRole.TEACHER),institutionId="campus")
        val groups=InMemoryAcademicGroupRepository();val classrooms=InMemoryClassroomRepository();val repository=InMemoryAttendanceRepository()
        lateinit var policy:AttendanceAccessPolicy;lateinit var service:AttendanceSessionService
        fun session(id:String="own",course:String="own-class",owner:String="teacher")=AttendanceSessionResponse(id,"occurrence-$id",course,"6A","2026-10-10","07:00","07:50",owner,now.epochSecond,now.plusSeconds(3600).epochSecond,status=AttendanceSessionStatus.OPEN,identitySource=AttendanceIdentitySource.NATIVE,institutionId=if(course=="foreign-class") "other" else "campus",groupId=if(course=="foreign-class") "6B" else "6A",groupScopeRevision=0)
        suspend fun initialize() {
            val accounts=ScopedAcademicFixtures.accounts(actor,teacher)
            groups.create(ScopedAcademicFixtures.group("6A").copy(institutionId="campus"));groups.create(ScopedAcademicFixtures.group("6B").copy(institutionId="other"))
            classrooms.createClassroom(NativeClassroom("own-class","Own",null,null,"6A","6A","teacher","Teacher",ClassroomStatus.ACTIVE,now,0,0))
            classrooms.createClassroom(NativeClassroom("foreign-class","Foreign",null,null,"6B","6B","outside","Outside",ClassroomStatus.ACTIVE,now,0,0))
            val classes=ClassroomService(classrooms,groups,Clock.fixed(now,ZoneOffset.UTC),accounts=accounts)
            val resolver=ProviderAttendanceOccurrenceResolver(MockAcademicProvider())
            policy=AttendanceAccessPolicy(repository,resolver,AttendanceAdministrationPolicy(accounts,classes))
            service=AttendanceSessionService(repository,resolver,policy,Clock.fixed(now,ZoneOffset.UTC))
            repository.createSession(session());repository.createSession(session("foreign","foreign-class","outside"))
        }
    }
    @Test fun `native administrative scope is tied to actual class rather than a global boolean`()=runTest {
        val f=Fixture();f.initialize()
        assertFailsWith<ApiException.Forbidden> { f.policy.requireOwnerOrAdministrative(f.actor.id,f.session(),true) }
        f.policy.requireOwnerOrAdministrative(f.actor.id,f.session(),true,f.actor)
        assertFailsWith<ApiException.Forbidden> { f.policy.requireOwnerOrAdministrative(f.actor.id,f.session("foreign","foreign-class","outside"),true,f.actor) }
        assertEquals(listOf("own"),f.service.activeForAdministration(f.actor).map { it.id })
    }
    @Test fun `native teacher cannot select institutional attendance by matching ID or stale owner strings`()=runTest {
        val f=Fixture();f.initialize()
        f.policy.requireOwnerOrAdministrative(f.teacher.id,f.session(),false,f.teacher)
        assertFailsWith<ApiException.Forbidden> { f.policy.requireOwnerOrAdministrative(f.teacher.id,f.session("collision","C-9001"),false,f.teacher) }
        assertFailsWith<ApiException.Forbidden> { f.policy.requireOwnerOrAdministrative(f.teacher.id,f.session("foreign","foreign-class","outside"),true,f.teacher) }
    }
    @Test fun `unprivileged or forged native actor cannot list all attendance sessions`()=runTest {
        val f=Fixture();f.initialize()
        assertFailsWith<ApiException.Forbidden> { f.service.activeForAdministration(f.teacher) }
        assertFailsWith<ApiException.Unauthorized> { f.service.activeForAdministration(f.actor.copy(institutionId="other")) }
    }
}
