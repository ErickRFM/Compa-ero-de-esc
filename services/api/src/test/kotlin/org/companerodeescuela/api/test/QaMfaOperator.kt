package org.companerodeescuela.api.test

import java.time.*
import org.companerodeescuela.api.auth.*
import org.companerodeescuela.api.security.*
import org.companerodeescuela.shared.contracts.*
import kotlin.test.assertTrue

/** Real crypto in test-only operator setup; no bypass/mock proof verifier. */
class QaClock(var now:Instant=Instant.now()):Clock() {
    override fun instant()=now
    override fun getZone():ZoneId=ZoneOffset.UTC
    override fun withZone(zone:ZoneId):Clock=this
}
class QaMfaOperator private constructor(val actor:PlatformAccount,val mfa:MfaService,val clock:QaClock,private val seed:ByteArray) {
    suspend fun proof(action:StepUpAction,resource:String,operation:String,digest:String):String {
        clock.now=clock.now.plusSeconds(30)
        return mfa.issueStepUp(actor,PASSWORD,Totp.code(seed,clock.now.epochSecond),action,resource,operation,digest).proof
    }
    companion object {
        private const val PASSWORD="operator-test-password"
        suspend fun create(accounts:PlatformAccountRepository,actor:UserSummary,clock:QaClock=QaClock()):QaMfaOperator {
            val current=ScopedAcademicFixtures.account(actor).copy(passwordHash=PasswordHasher().hash(PASSWORD),
                adminCapabilities=setOf(AdminCapability.MANAGE_ACADEMICS,AdminCapability.APPROVE_STAFF,AdminCapability.MANAGE_USERS))
            assertTrue(accounts.create(current))
            val mfa=MfaService(accounts,InMemoryMfaRepository(),MfaSecretCipher(ByteArray(32){it.toByte()}),clock)
            val enrollment=mfa.beginEnrollment(current,PASSWORD)
            val seed=Totp.decodeSecret(enrollment.secret)
            mfa.confirmEnrollment(current,enrollment.enrollmentId,Totp.code(seed,clock.now.epochSecond))
            return QaMfaOperator(current,mfa,clock,seed)
        }
    }
}
