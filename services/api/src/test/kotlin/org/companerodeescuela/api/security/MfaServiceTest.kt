package org.companerodeescuela.api.security

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import org.companerodeescuela.api.auth.*
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.*

class MfaServiceTest {
    private val password="operator-test-password"
    private val digest="a".repeat(64)
    @Test fun `verified operator enrolls real factor and gets exact scoped proof with no raw stored proof`() = runTest {
        val f=fixture();val enrollment=f.service.beginEnrollment(f.actor,password)
        assertEquals(32,enrollment.secret.length);assertFalse(enrollment.toString().contains(enrollment.secret))
        val stored=assertNotNull(f.repository.findFactor(f.actor.id));assertFalse(stored.encryptedSecret.contains(enrollment.secret))
        assertEquals(600L,enrollment.expiresAtEpochSeconds-f.clock.instant().epochSecond)
        assertFalse(f.service.status(f.actor).enabled)
        assertTrue(f.service.confirmEnrollment(f.actor,enrollment.enrollmentId,f.code(enrollment)).enabled)
        f.clock.advance(30)
        val proof=f.service.issueStepUp(f.actor,password,f.code(enrollment),StepUpAction.APPROVE_STAFF,"target","operation",digest)
        assertEquals(43,proof.proof.length);assertFalse(proof.toString().contains(proof.proof))
        f.service.requireStepUp(f.actor,proof.proof,StepUpAction.APPROVE_STAFF,"target","operation",digest)
        f.service.requireStepUp(f.actor,proof.proof,StepUpAction.APPROVE_STAFF,"target","operation",digest)
        assertFailsWith<ApiException.Forbidden> { f.service.requireStepUp(f.actor,proof.proof,StepUpAction.APPROVE_STAFF,"other","operation",digest) }
        assertFailsWith<ApiException.Forbidden> { f.service.requireStepUp(f.actor,proof.proof,StepUpAction.APPROVE_STAFF,"target","operation","b".repeat(64)) }
        f.clock.advance(300)
        assertFailsWith<ApiException.Forbidden> { f.service.requireStepUp(f.actor,proof.proof,StepUpAction.APPROVE_STAFF,"target","operation",digest) }
    }
    @Test fun `enrollment reauth wrong OTP stale enrollment and expired challenge never enable MFA`() = runTest {
        val f=fixture()
        assertFailsWith<ApiException.Forbidden> { f.service.beginEnrollment(f.actor,"wrong-password") }
        val first=f.service.beginEnrollment(f.actor,password);val second=f.service.beginEnrollment(f.actor,password)
        assertNotEquals(first.secret,second.secret)
        assertFailsWith<ApiException.Forbidden> { f.service.confirmEnrollment(f.actor,first.enrollmentId,f.code(first)) }
        assertFailsWith<ApiException.Forbidden> { f.service.confirmEnrollment(f.actor,second.enrollmentId,"bad-otp") }
        f.clock.advance(600)
        assertFailsWith<ApiException.Forbidden> { f.service.confirmEnrollment(f.actor,second.enrollmentId,f.code(second)) }
        assertFalse(f.service.status(f.actor).enabled)
    }
    @Test fun `concurrent proof creation from one OTP has one winner and rejects replay`() = runTest {
        val f=fixture();val enrolled=f.enroll();f.clock.advance(30);val code=f.code(enrolled)
        val results=coroutineScope { (1..16).map { i -> async(Dispatchers.Default) { runCatching { f.service.issueStepUp(f.actor,password,code,StepUpAction.APPROVE_STAFF,"target","op-$i",digest) }.getOrNull() } }.awaitAll() }
        assertEquals(1,results.count { it!=null })
        assertFailsWith<ApiException.Forbidden> { f.service.issueStepUp(f.actor,password,code,StepUpAction.APPROVE_STAFF,"target","replay",digest) }
        assertFailsWith<ApiException.Forbidden> { f.service.beginEnrollment(f.actor,password) }
    }
    @Test fun `authority change invalidates old proof but does not destroy the enrolled individual factor`() = runTest {
        val f=fixture();val enrolled=f.enroll();f.clock.advance(30)
        val proof=f.service.issueStepUp(f.actor,password,f.code(enrolled),StepUpAction.APPROVE_STAFF,"target","op",digest)
        val updated=assertNotNull(f.accounts.changeIdentity(f.actor.id,0,AccountIdentityChange("change","operator",AccountStatus.ACTIVE,f.actor.roles,"campus-a",f.clock.instant())))
        assertFailsWith<ApiException.Unauthorized> { f.service.requireStepUp(f.actor,proof.proof,StepUpAction.APPROVE_STAFF,"target","op",digest) }
        assertFailsWith<ApiException.Forbidden> { f.service.requireStepUp(updated,proof.proof,StepUpAction.APPROVE_STAFF,"target","op",digest) }
        assertTrue(f.service.status(updated).enabled)
        f.clock.advance(30)
        assertEquals(43,f.service.issueStepUp(updated,password,f.code(enrolled),StepUpAction.APPROVE_STAFF,"target","new-op",digest).proof.length)
    }
    @Test fun `suspension revoked snapshot or unverified email cannot enroll or issue a proof`() = runTest {
        val f=fixture();val enrolled=f.enroll()
        assertFailsWith<ApiException.Unauthorized> { f.service.beginEnrollment(f.actor.copy(authRevision=2),password) }
        assertFailsWith<ApiException.Unauthorized> { f.service.beginEnrollment(f.actor.copy(roles=setOf(UserRole.STUDENT)),password) }
        f.accounts.changeIdentity(f.actor.id,0,AccountIdentityChange("suspend","operator",AccountStatus.SUSPENDED,f.actor.roles,"campus-a",f.clock.instant()))
        assertFailsWith<ApiException.Unauthorized> { f.service.issueStepUp(f.actor,password,f.code(enrolled),StepUpAction.APPROVE_STAFF,"target","op",digest) }
        val unverified=fixture(emailVerified=false)
        assertFailsWith<ApiException.Forbidden> { unverified.service.beginEnrollment(unverified.actor,password) }
    }
    @Test fun `basic participant can prepare invited privileged acceptance but never administrative approval`() = runTest {
        val f=fixture(roles=setOf(UserRole.WORKSHOP_PARTICIPANT));val enrolled=f.enroll();f.clock.advance(30)
        assertFailsWith<ApiException.Forbidden> { f.service.issueStepUp(f.actor,password,f.code(enrolled),StepUpAction.APPROVE_STAFF,"target","op",digest) }
        val proof=f.service.issueStepUp(f.actor,password,f.code(enrolled),StepUpAction.PRIVILEGED_INVITE_ACCEPT,"invite","op",digest)
        assertEquals(43,proof.proof.length)
        val admin=fixture(roles=setOf(UserRole.ADMIN));val adminEnrollment=admin.enroll();admin.clock.advance(30)
        assertFailsWith<ApiException.Forbidden> { admin.service.issueStepUp(admin.actor,password,admin.code(adminEnrollment),StepUpAction.ADMIN_AUTHORITY,"self","op",digest) }
    }
    @Test fun `missing key never enables production fallback and secrets are redacted`() = runTest {
        val f=fixture();val unavailable=MfaService(f.accounts,f.repository,null,f.clock)
        assertFalse(unavailable.status(f.actor).available)
        assertFailsWith<ApiException.DependencyUnavailable> { unavailable.beginEnrollment(f.actor,password) }
        assertEquals("MfaEnrollmentRequest([redacted])",MfaEnrollmentRequest(password).toString())
        assertFalse(MfaStepUpRequest(password,"123456",StepUpAction.APPROVE_STAFF,"target","op",digest).toString().contains(password))
    }
    private suspend fun fixture(roles:Set<UserRole> = setOf(UserRole.SUPER_ADMIN),emailVerified:Boolean = true):Fixture {
        val clock=MutableClock();val accounts=InMemoryPlatformAccountRepository();val repo=InMemoryMfaRepository()
        val actor=PlatformAccount("operator","QA operator","qa-operator@example.test",PasswordHasher().hash(password),roles,
            institutionId="campus-a",emailVerifiedAt=if(emailVerified) clock.instant() else null)
        assertTrue(accounts.create(actor));return Fixture(accounts,repo,clock,actor,MfaService(accounts,repo,MfaSecretCipher(ByteArray(32) { (it+1).toByte() }),clock))
    }
    private data class Fixture(val accounts:InMemoryPlatformAccountRepository,val repository:InMemoryMfaRepository,val clock:MutableClock,val actor:PlatformAccount,val service:MfaService) {
        fun code(enrollment:MfaEnrollment)=Totp.code(Totp.decodeSecret(enrollment.secret),clock.instant().epochSecond)
        suspend fun enroll()=service.beginEnrollment(actor,"operator-test-password").also { service.confirmEnrollment(actor,it.enrollmentId,code(it)) }
    }
    private class MutableClock : Clock() {
        private var now=Instant.parse("2026-10-10T06:00:00Z")
        override fun getZone():ZoneId=ZoneOffset.UTC
        override fun withZone(zone:ZoneId):Clock=this
        override fun instant():Instant=now
        fun advance(seconds:Long) { now=now.plusSeconds(seconds) }
    }
}
