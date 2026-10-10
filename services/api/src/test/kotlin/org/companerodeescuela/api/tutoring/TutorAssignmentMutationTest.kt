package org.companerodeescuela.api.tutoring

import java.time.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*
import org.companerodeescuela.api.auth.*
import org.companerodeescuela.api.academic.groups.*
import org.companerodeescuela.api.test.ScopedAcademicFixtures
import org.companerodeescuela.api.security.*
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.*

class TutorAssignmentMutationTest {
    private val password="operator-test-password"
    private class TestClock:Clock() {
        var now=Instant.parse("2026-10-10T07:00:00Z")
        override fun instant()=now
        override fun getZone():ZoneId=ZoneOffset.UTC
        override fun withZone(zone:ZoneId):Clock=this
    }
    private inner class Fixture {
        val clock=TestClock();val accounts=InMemoryPlatformAccountRepository();val groups=InMemoryAcademicGroupRepository();val assignments=InMemoryTutorAssignmentRepository()
        val actor=PlatformAccount("admin","QA Admin","admin@example.test",PasswordHasher().hash(password),setOf(UserRole.ADMIN),institutionId="campus",emailVerifiedAt=clock.now,adminCapabilities=setOf(AdminCapability.MANAGE_ACADEMICS))
        val tutor=PlatformAccount("tutor","QA Tutor","tutor@example.test","test-hash",setOf(UserRole.TUTOR),institutionId="campus",emailVerifiedAt=clock.now)
        val mfa=MfaService(accounts,InMemoryMfaRepository(),MfaSecretCipher(ByteArray(32){it.toByte()}),clock)
        val service=TutorAssignmentService(assignments,groups,clock,accounts,mfa)
        lateinit var secret:ByteArray
        suspend fun init() {
            accounts.create(actor);accounts.create(tutor)
            groups.create(AcademicGroupRecord("6A","6A",true,clock.now,"campus","period",clock.now,clock.now.plusSeconds(3600)))
            groups.create(AcademicGroupRecord("OTHER","Other",true,clock.now,"other","period",clock.now,clock.now.plusSeconds(3600)))
            val enrollment=mfa.beginEnrollment(actor,password);secret=Totp.decodeSecret(enrollment.secret)
            mfa.confirmEnrollment(actor,enrollment.enrollmentId,Totp.code(secret,clock.now.epochSecond))
        }
        fun request(group:String="6A")=CreateTutorAssignmentRequest("tutor",group,"campus","period",Instant.parse("2026-10-10T07:00:00Z").epochSecond,Instant.parse("2026-10-10T08:00:00Z").epochSecond,"assign",null)
        suspend fun proof(resource:String,operation:String,digest:String):String {
            clock.now=clock.now.plusSeconds(30)
            return mfa.issueStepUp(actor,password,Totp.code(secret,clock.now.epochSecond),StepUpAction.MANAGE_ACADEMICS,resource,operation,digest).proof
        }
    }
    @Test fun `direct assignment mutation requires real scoped MFA and full matching tutor group metadata`()=runBlocking<Unit> {
        val f=Fixture();f.init();val request=f.request()
        assertFailsWith<ApiException.Forbidden> { f.service.create(f.actor.toUserSummary(),request) }
        val proof=f.proof("6A","assign",PrivilegedCommandDigests.tutorAssignment("6A",request))
        val assigned=f.service.create(f.actor.toUserSummary(),request.copy(proof=proof))
        assertEquals("campus",assigned.institutionId);assertEquals("period",assigned.periodId);assertEquals(0,assigned.tutorRoleGeneration)
        assertEquals(listOf("6A"),f.service.scopeFor(f.tutor.toUserSummary()).groups.map { it.id })
        assertEquals(assigned,f.service.create(f.actor.toUserSummary(),request.copy(proof=proof)))
        assertFailsWith<ApiException.Forbidden> { f.service.create(f.actor.toUserSummary(),request.copy(proof=proof,endsAtEpochSeconds=request.endsAtEpochSeconds!!-1)) }
    }
    @Test fun `self assignment cross institution and unverified target are denied before granting access`()=runBlocking<Unit> {
        val f=Fixture();f.init()
        assertFailsWith<ApiException.Forbidden> { f.service.create(f.tutor.toUserSummary(),f.request()) }
        assertFailsWith<ApiException.Forbidden> { f.service.create(f.actor.toUserSummary(),f.request("OTHER").copy(institutionId="other")) }
        f.accounts.create(f.tutor.copy(id="unverified",email="unverified@example.test",emailVerifiedAt=null))
        val request=f.request().copy(tutorUserId="unverified")
        val proof=f.proof("6A","assign",PrivilegedCommandDigests.tutorAssignment("6A",request))
        assertFailsWith<ApiException.Validation> { f.service.create(f.actor.toUserSummary(),request.copy(proof=proof)) }
        assertTrue(f.assignments.listAll().isEmpty())
    }
    @Test fun `revocation requires current institution and proof and preserves exact retry`()=runBlocking<Unit> {
        val f=Fixture();f.init();val request=f.request()
        val assigned=f.service.create(f.actor.toUserSummary(),request.copy(proof=f.proof("6A","assign",PrivilegedCommandDigests.tutorAssignment("6A",request))))
        assertFailsWith<ApiException.Forbidden> { f.service.revoke(f.actor.toUserSummary(),assigned.id) }
        var revoke=RevokeTutorAssignmentRequest("revoke","")
        revoke=revoke.copy(proof=f.proof(assigned.id,revoke.operationId,PrivilegedCommandDigests.tutorRevocation(assigned.id,revoke)))
        val result=f.service.revoke(f.actor.toUserSummary(),assigned.id,revoke)
        assertFalse(result.active);assertEquals(result,f.service.revoke(f.actor.toUserSummary(),assigned.id,revoke))
        assertTrue(f.service.scopeFor(f.tutor.toUserSummary()).groups.isEmpty())
        assertFailsWith<ApiException.Forbidden> { f.service.requireTutorAssignmentForGroup(f.tutor.toUserSummary(),"6A") }
    }
}
