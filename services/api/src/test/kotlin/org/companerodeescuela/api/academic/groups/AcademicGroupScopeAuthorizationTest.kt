package org.companerodeescuela.api.academic.groups

import java.time.Instant
import kotlinx.coroutines.*
import kotlin.test.*
import org.companerodeescuela.api.auth.*
import org.companerodeescuela.api.institutions.InMemoryInstitutionRepository
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.test.*
import org.companerodeescuela.shared.contracts.*

class AcademicGroupScopeAuthorizationTest {
    private val now=Instant.parse("2026-10-10T07:00:00Z")
    private inner class Fixture {
        val clock=QaClock(now);val accounts=InMemoryPlatformAccountRepository();val groups=InMemoryAcademicGroupRepository()
        lateinit var operator:QaMfaOperator;lateinit var service:AcademicGroupService
        suspend fun initialize(role:UserRole=UserRole.ADMIN) {
            operator=QaMfaOperator.create(accounts,UserSummary("operator","QA Operator",roles=setOf(role),institutionId=if(role==UserRole.ADMIN) "campus" else null),clock)
            service=AcademicGroupService(groups,clock,accounts,operator.mfa,InMemoryInstitutionRepository(listOf(InstitutionSummary("campus","QA Campus"),InstitutionSummary("other","QA Other"))))
            groups.create(AcademicGroupRecord("6A","6A",true,now,"campus","period",now,now.plusSeconds(3600)))
            groups.create(AcademicGroupRecord("LEGACY","Legacy",true,now))
            groups.create(AcademicGroupRecord("OTHER","Other",true,now,"other","period",now,now.plusSeconds(3600)))
        }
        fun command(revision:Long=0,period:String="next")=AcademicGroupScopeRequest("operation",revision,"campus",period,now.epochSecond,now.plusSeconds(3600).epochSecond,"")
        suspend fun proof(group:String,command:AcademicGroupScopeRequest)=command.copy(proof=operator.proof(StepUpAction.MANAGE_ACADEMICS,group,command.operationId,PrivilegedCommandDigests.groupScope(group,command)))
    }
    @Test fun `own group migration requires real proof and exact parallel retries retain one audit`()=runBlocking<Unit> {
        val f=Fixture();f.initialize()
        assertFailsWith<ApiException.Forbidden> { f.service.changeScope(f.operator.actor.toUserSummary(),"6A",f.command()) }
        val command=f.proof("6A",f.command())
        val results=coroutineScope { (1..24).map { async { f.service.changeScope(f.operator.actor.toUserSummary(),"6A",command) } }.awaitAll() }
        assertTrue(results.all { it.scopeRevision==1L && it.periodId=="next" })
        assertEquals(1,f.groups.find("6A")?.scopeAudit?.size)
        assertFailsWith<ApiException.Forbidden> { f.service.changeScope(f.operator.actor.toUserSummary(),"6A",command.copy(periodId="changed")) }
    }
    @Test fun `legacy institution can only be explicitly set by verified super admin`()=runBlocking<Unit> {
        val admin=Fixture();admin.initialize()
        assertFailsWith<ApiException.Forbidden> { admin.service.changeScope(admin.operator.actor.toUserSummary(),"LEGACY",admin.proof("LEGACY",admin.command())) }
        val owner=Fixture();owner.initialize(UserRole.SUPER_ADMIN)
        val result=owner.service.changeScope(owner.operator.actor.toUserSummary(),"LEGACY",owner.proof("LEGACY",owner.command()))
        assertEquals("campus",result.institutionId);assertEquals(1,result.scopeRevision)
        assertEquals("operator",owner.groups.find("LEGACY")?.scopeAudit?.single()?.actorId)
    }
    @Test fun `even global owner cannot move group data across institutions or invent directory entries`()=runBlocking<Unit> {
        val f=Fixture();f.initialize(UserRole.SUPER_ADMIN)
        assertFailsWith<ApiException.Forbidden> { f.service.changeScope(f.operator.actor.toUserSummary(),"OTHER",f.proof("OTHER",f.command())) }
        assertFailsWith<ApiException.Validation> { f.service.changeScope(f.operator.actor.toUserSummary(),"LEGACY",f.proof("LEGACY",f.command().copy(institutionId="missing"))) }
        assertEquals("other",f.groups.find("OTHER")?.institutionId);assertNull(f.groups.find("LEGACY")?.institutionId)
    }
    @Test fun `legacy members of another or unlinked institution block migration rather than guessing identities`()=runBlocking<Unit> {
        val f=Fixture();f.initialize(UserRole.SUPER_ADMIN)
        val student=PlatformAccount("student","QA Student","student@example.test","test-hash",setOf(UserRole.STUDENT),emailVerifiedAt=now)
        f.accounts.create(student);f.groups.assign(AcademicGroupMembershipRecord("LEGACY",student.id,now))
        assertFailsWith<ApiException.Conflict> { f.service.changeScope(f.operator.actor.toUserSummary(),"LEGACY",f.proof("LEGACY",f.command())) }
        assertNull(f.groups.find("LEGACY")?.institutionId)
    }
    @Test fun `admin group listing is current institution scoped and creation needs explicit bounded proof`()=runBlocking<Unit> {
        val f=Fixture();f.initialize();val actor=f.operator.actor.toUserSummary()
        assertEquals(listOf("6A"),f.service.listFor(actor).map { it.id })
        val raw=CreateAcademicGroupRequest("7a","7A","campus","period",now.epochSecond,now.plusSeconds(3600).epochSecond,"create",null)
        assertFailsWith<ApiException.Forbidden> { f.service.create(actor,raw) }
        val command=raw.copy(proof=f.operator.proof(StepUpAction.MANAGE_ACADEMICS,"7A",raw.operationId!!,PrivilegedCommandDigests.groupCreation("7A",raw)))
        val results=coroutineScope { (1..12).map { async { f.service.create(actor,command) } }.awaitAll() }
        assertTrue(results.all { it.institutionId=="campus" && it.periodId=="period" })
        assertEquals(1,f.groups.find("7A")?.scopeAudit?.size)
        assertFailsWith<ApiException.Forbidden> { f.service.create(actor,command.copy(institutionId="other")) }
    }
    @Test fun `member assignment validates live student and group tenant before using exact step up`()=runBlocking<Unit> {
        val f=Fixture();f.initialize();val actor=f.operator.actor.toUserSummary()
        val own=PlatformAccount("student","QA Student","student@example.test","test-hash",setOf(UserRole.STUDENT),institutionId="campus",emailVerifiedAt=now)
        f.accounts.create(own);f.accounts.create(own.copy(id="other-student",email="other-student@example.test",institutionId="other"))
        var request=AssignAcademicGroupMemberRequest("student","member")
        assertFailsWith<ApiException.Forbidden> { f.service.assignMember(actor,"6A",request) }
        request=request.copy(proof=f.operator.proof(StepUpAction.MANAGE_ACADEMICS,"6A",request.operationId!!,PrivilegedCommandDigests.groupMembership("6A",request)))
        val results=coroutineScope { (1..12).map { async { f.service.assignMember(actor,"6A",request) } }.awaitAll() }
        assertTrue(results.all { it.userId=="student" });assertEquals(listOf("student"),f.groups.memberIdsForGroup("6A"))
        assertEquals(listOf("6A"),f.service.listFor(own.toUserSummary()).map { it.id })
        val wrong=AssignAcademicGroupMemberRequest("other-student","outside")
        val proof=f.operator.proof(StepUpAction.MANAGE_ACADEMICS,"6A",wrong.operationId!!,PrivilegedCommandDigests.groupMembership("6A",wrong))
        assertFailsWith<ApiException.Validation> { f.service.assignMember(actor,"6A",wrong.copy(proof=proof)) }
        assertFalse(f.groups.isUserInGroup("other-student","6A"))
    }
    @Test fun `wrong tenant absent capability invalid dates and stale revision cannot alter group`()=runBlocking<Unit> {
        val f=Fixture();f.initialize()
        assertFailsWith<ApiException.Forbidden> { f.service.changeScope(f.operator.actor.toUserSummary(),"OTHER",f.command().copy(institutionId="other")) }
        assertFailsWith<ApiException.Validation> { f.service.changeScope(f.operator.actor.toUserSummary(),"6A",f.proof("6A",f.command().copy(endsAtEpochSeconds=now.epochSecond))) }
        assertFailsWith<ApiException.Conflict> { f.service.changeScope(f.operator.actor.toUserSummary(),"6A",f.proof("6A",f.command(revision=2))) }
        val current=f.operator.actor
        val narrowed=assertNotNull(f.accounts.changeIdentity(current.id,0,AccountIdentityChange("narrow","owner",AccountStatus.ACTIVE,current.roles,"campus",f.clock.now,capabilities=emptySet())))
        assertFailsWith<ApiException.Forbidden> { f.service.changeScope(narrowed.toUserSummary(),"6A",f.command()) }
        assertEquals(0,f.groups.find("6A")?.scopeRevision)
    }
}

