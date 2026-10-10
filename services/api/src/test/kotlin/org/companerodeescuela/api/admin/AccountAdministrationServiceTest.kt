package org.companerodeescuela.api.admin

import java.time.*
import kotlinx.coroutines.*
import kotlin.test.*
import org.companerodeescuela.api.auth.*
import org.companerodeescuela.api.academic.groups.*
import org.companerodeescuela.api.institutions.InMemoryInstitutionRepository
import org.companerodeescuela.api.tutoring.*
import org.companerodeescuela.api.security.*
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.*

class AccountAdministrationServiceTest {
    private val password="operator-test-password"
    private class TestClock:Clock() {
        var now=Instant.parse("2026-10-10T07:00:00Z")
        override fun instant()=now
        override fun getZone():ZoneId=ZoneOffset.UTC
        override fun withZone(zone:ZoneId):Clock=this
    }
    private inner class Fixture {
        val clock=TestClock();val accounts=InMemoryPlatformAccountRepository();val groups=InMemoryAcademicGroupRepository()
        val assignments=InMemoryTutorAssignmentRepository()
        val directory=InMemoryInstitutionRepository(listOf(InstitutionSummary("campus","QA Campus"),InstitutionSummary("other","Other QA campus")))
        val mfa=MfaService(accounts,InMemoryMfaRepository(),MfaSecretCipher(ByteArray(32){it.toByte()}),clock)
        val tutoring=TutorAssignmentService(assignments,groups,clock,accounts)
        val service=AccountAdministrationService(accounts,directory,groups,tutoring,mfa,clock)
        lateinit var actor:PlatformAccount;lateinit var seed:ByteArray
        suspend fun initialize(role:UserRole=UserRole.ADMIN) {
            actor=PlatformAccount("admin","QA Operator","admin@example.test",PasswordHasher().hash(password),setOf(role),institutionId=if(role==UserRole.ADMIN) "campus" else null,emailVerifiedAt=clock.now,adminCapabilities=setOf(AdminCapability.APPROVE_STAFF,AdminCapability.MANAGE_USERS,AdminCapability.MANAGE_ACADEMICS))
            accounts.create(actor)
            val enrollment=mfa.beginEnrollment(actor,password);seed=Totp.decodeSecret(enrollment.secret)
            mfa.confirmEnrollment(actor,enrollment.enrollmentId,Totp.code(seed,clock.now.epochSecond))
            for(id in listOf("6A","6B")) groups.create(AcademicGroupRecord(id,id,true,clock.now,"campus","period",clock.now,clock.now.plusSeconds(3600)))
            groups.create(AcademicGroupRecord("OTHER","Other",true,clock.now,"other","period",clock.now,clock.now.plusSeconds(3600)))
        }
        suspend fun pending(profile:RegistrationAccountType=RegistrationAccountType.TEACHER,id:String="pending",institution:String="campus"):PlatformAccount {
            val account=PlatformAccount(id,"Pending Applicant","$id@example.test","test-hash",emptySet(),accountStatus=AccountStatus.PENDING_APPROVAL,emailVerifiedAt=clock.now,registrationIntent=RegistrationIntent(profile,institution,"identity-reference","6A"))
            accounts.create(account);return account
        }
        suspend fun proof(target:String,request:ApproveStaffRequest):ApproveStaffRequest {
            clock.now=clock.now.plusSeconds(30)
            val proof=mfa.issueStepUp(actor,password,Totp.code(seed,clock.now.epochSecond),StepUpAction.APPROVE_STAFF,target,request.operationId,PrivilegedCommandDigests.staffApproval(target,request))
            return request.copy(proof=proof.proof)
        }
        fun request(application:String="registration-pending",scopes:List<TutorApprovalScope> = emptyList())=ApproveStaffRequest("approve",0,application,"campus",scopes,"")
        fun scopes()=listOf("6A","6B").map { TutorApprovalScope(it,"period",Instant.parse("2026-10-10T07:00:00Z").epochSecond,Instant.parse("2026-10-10T08:00:00Z").epochSecond) }
    }
    @Test fun `verified pending teacher receives only persisted teacher intent after actual scoped MFA`()=runBlocking<Unit> {
        val f=Fixture();f.initialize();f.pending()
        assertFailsWith<ApiException.Forbidden> { f.service.approveStaff(f.actor,"pending",f.request()) }
        val approved=f.service.approveStaff(f.actor,"pending",f.proof("pending",f.request()))
        assertEquals(AccountStatus.ACTIVE,approved.user.accountStatus);assertEquals(setOf(UserRole.TEACHER),approved.user.roles)
        assertEquals("campus",approved.user.institutionId);assertEquals(StaffApplicationStatus.APPROVED,approved.applications.single().status)
        assertTrue(f.assignments.listAll().isEmpty())
    }
    @Test fun `teacher supplementary tutor application remains preference until approval assigns multiple real groups`()=runBlocking<Unit> {
        val f=Fixture();f.initialize();val teacher=PlatformAccount("teacher","QA Teacher","teacher@example.test","test-hash",setOf(UserRole.TEACHER),institutionId="campus",emailVerifiedAt=f.clock.now);f.accounts.create(teacher)
        val application=f.service.requestTutor(teacher,TutorApplicationRequest("request",0,"campus","reference","OTHER"))
        assertEquals(StaffApplicationStatus.PENDING,application.status);assertEquals(setOf(UserRole.TEACHER),f.accounts.findById("teacher")?.roles)
        assertTrue(f.assignments.listAll().isEmpty())
        val request=f.proof("teacher",f.request(application.id,f.scopes()))
        val approved=f.service.approveStaff(f.actor,"teacher",request)
        assertEquals(setOf(UserRole.TEACHER,UserRole.TUTOR),approved.user.roles)
        assertEquals(listOf("6A","6B"),f.tutoring.scopeFor(approved.user).groups.map { it.id }.sorted())
        assertFailsWith<ApiException.Forbidden> { f.tutoring.requireTutorAssignmentForGroup(approved.user,"OTHER") }
    }
    @Test fun `parallel same operation approval is idempotent and changed group payload cannot reuse proof`()=runBlocking<Unit> {
        val f=Fixture();f.initialize();f.pending(RegistrationAccountType.TUTOR)
        val request=f.proof("pending",f.request(scopes=f.scopes()))
        val results=coroutineScope { (1..24).map { async { f.service.approveStaff(f.actor,"pending",request) } }.awaitAll() }
        assertTrue(results.all { it.user.roles==setOf(UserRole.TUTOR) });assertEquals(1,f.accounts.findById("pending")?.identityAudit?.size)
        assertEquals(2,f.assignments.listAll().size)
        assertFailsWith<ApiException.Forbidden> { f.service.approveStaff(f.actor,"pending",request.copy(tutorScopes=listOf(f.scopes().first()))) }
        assertFailsWith<ApiException.Conflict> { f.service.approveStaff(f.actor,"pending",f.proof("pending",request.copy(operationId="another"))) }
    }
    @Test fun `native institutional admin cannot list approve or suspend another institution or self escalate`()=runBlocking<Unit> {
        val f=Fixture();f.initialize();f.pending(id="own");f.pending(id="outside",institution="other")
        val overview=f.service.overview(f.actor)
        assertEquals(setOf("admin","own"),overview.users.map { it.user.id }.toSet())
        assertEquals(2,overview.metrics.totalUsers);assertEquals(1,overview.metrics.pendingStaff)
        assertFailsWith<ApiException.Forbidden> { f.service.approveStaff(f.actor,"outside",f.request("registration-outside").copy(institutionId="other")) }
        assertFailsWith<ApiException.Forbidden> { f.service.lifecycle(f.actor,"outside",AccountLifecycleRequest("suspend",0,AccountStatus.SUSPENDED,"")) }
        assertFailsWith<ApiException.Unauthorized> { f.service.overview(f.actor.copy(roles=setOf(UserRole.SUPER_ADMIN))) }
        assertFailsWith<ApiException.Forbidden> { f.service.approveStaff(f.actor,"admin",f.request()) }
        assertEquals(setOf(UserRole.ADMIN),f.accounts.findById("admin")?.roles)
    }
    @Test fun `suspended target revoked actor inactive group or wrong period cannot be approved`()=runBlocking<Unit> {
        val f=Fixture();f.initialize();val pending=f.pending(RegistrationAccountType.TUTOR)
        val wrong=f.proof("pending",f.request(scopes=listOf(f.scopes().first().copy(periodId="old"))))
        assertFailsWith<ApiException.Validation> { f.service.approveStaff(f.actor,"pending",wrong) }
        assertTrue(f.accounts.findById("pending")?.roles.orEmpty().isEmpty())
        f.accounts.changeIdentity(pending.id,0,AccountIdentityChange("suspend","admin",AccountStatus.SUSPENDED,emptySet(),null,f.clock.now))
        assertFailsWith<ApiException.Conflict> { f.service.approveStaff(f.actor,"pending",f.proof("pending",f.request(scopes=f.scopes()))) }
        f.accounts.changeIdentity("admin",0,AccountIdentityChange("revoke","owner",AccountStatus.REVOKED,f.actor.roles,"campus",f.clock.now))
        assertFailsWith<ApiException.Unauthorized> { f.service.overview(f.actor) }
    }
    @Test fun `privileged lifecycle needs proof and preserves grants only within authorized scope`()=runBlocking<Unit> {
        val f=Fixture();f.initialize();val target=f.pending()
        assertFailsWith<ApiException.Forbidden> { f.service.lifecycle(f.actor,target.id,AccountLifecycleRequest("suspend",0,AccountStatus.SUSPENDED,"")) }
        var request=AccountLifecycleRequest("suspend",0,AccountStatus.SUSPENDED,"")
        f.clock.now=f.clock.now.plusSeconds(30)
        val proof=f.mfa.issueStepUp(f.actor,password,Totp.code(f.seed,f.clock.now.epochSecond),StepUpAction.ACCOUNT_LIFECYCLE,target.id,request.operationId,PrivilegedCommandDigests.accountLifecycle(target.id,request))
        request=request.copy(proof=proof.proof)
        val suspended=f.service.lifecycle(f.actor,target.id,request)
        assertEquals(AccountStatus.SUSPENDED,suspended.user.accountStatus);assertFalse(suspended.user.active)
        assertEquals(suspended,f.service.lifecycle(f.actor,target.id,request))
        assertFailsWith<ApiException.Forbidden> { f.service.lifecycle(f.actor,target.id,request.copy(status=AccountStatus.ACTIVE)) }
    }

    @Test fun `staff reviewer can choose actual current scoped group metadata without gaining academic management or member data`()=runBlocking<Unit> {
        val f=Fixture();f.initialize()
        val reviewer=PlatformAccount("reviewer","Reviewer","reviewer@example.test","test-hash",setOf(UserRole.ADMIN),institutionId="campus",emailVerifiedAt=f.clock.now,adminCapabilities=setOf(AdminCapability.APPROVE_STAFF))
        f.accounts.create(reviewer)
        f.groups.create(AcademicGroupRecord("unknown","Unknown",true,f.clock.now))
        f.groups.create(AcademicGroupRecord("expired","Expired",true,f.clock.now,"campus","period",f.clock.now.minusSeconds(3600),f.clock.now.minusSeconds(1)))
        val groups=f.service.approvalGroups(reviewer)
        assertEquals(listOf("6A","6B"),groups.map { it.id })
        assertTrue(groups.all { it.institutionId=="campus" && it.periodId=="period" && it.endsAtEpochSeconds==f.clock.now.plusSeconds(3600).epochSecond })
        val manager=reviewer.copy(id="manager",email="manager@example.test",adminCapabilities=setOf(AdminCapability.MANAGE_ACADEMICS))
        f.accounts.create(manager)
        assertFailsWith<ApiException.Forbidden> { f.service.approvalGroups(manager) }
        assertFailsWith<ApiException.Unauthorized> { f.service.approvalGroups(reviewer.copy(institutionId="other")) }
    }
}
