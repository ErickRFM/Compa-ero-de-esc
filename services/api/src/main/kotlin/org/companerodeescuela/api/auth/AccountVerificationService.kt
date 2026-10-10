package org.companerodeescuela.api.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.util.Base64
import java.util.UUID
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.mail.VerificationEmailGateway
import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.VerificationDeliveryReceipt

class AccountVerificationService(private val accounts: PlatformAccountRepository,
    private val email: VerificationEmailGateway, private val clock: Clock = Clock.systemUTC(),
    private val random: SecureRandom = SecureRandom()) {
    suspend fun issue(userId: String): VerificationDeliveryReceipt = authenticationDependency {
        val account = accounts.findById(userId) ?: throw ApiException.Unauthorized()
        if (account.accountStatus != AccountStatus.PENDING_VERIFICATION || !account.permitsSession ||
            account.registrationIntent == null || account.emailVerifiedAt != null) throw ApiException.Validation("Email verification is not pending")
        val now = clock.instant()
        val bytes = ByteArray(32).also(random::nextBytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        bytes.fill(0)
        val tokenHash = hash(token)
        if (!accounts.issueEmailVerification(userId, account.authRevision, tokenHash, now, now.plusSeconds(3600))) {
            val latest = accounts.findById(userId) ?: throw ApiException.Unauthorized()
            if (!latest.permitsSession || latest.authRevision != account.authRevision ||
                latest.accountStatus != AccountStatus.PENDING_VERIFICATION) throw ApiException.Unauthorized()
            val challenge = latest.emailVerification
            val history = challenge?.recentIssuedAt.orEmpty().filter { it > now.minusSeconds(3600) }
            val cooldown = challenge?.issuedAt?.plusSeconds(60)?.epochSecond ?: now.epochSecond
            val quota = if (history.size >= 3) history.minOf { it.epochSecond } + 3600 else now.epochSecond
            val retry = (maxOf(cooldown, quota) - now.epochSecond).coerceAtLeast(1)
            throw ApiException.RateLimited("Wait before requesting another verification email", retry)
        }
        val status = email.send(account.email, token, "email-verification-${UUID.randomUUID()}")
        accounts.recordEmailDelivery(userId, tokenHash, status)
        VerificationDeliveryReceipt(status, 60)
    }
    suspend fun verify(userId: String, token: String): PlatformAccount = authenticationDependency {
        if (!Regex("[A-Za-z0-9_-]{43}").matches(token)) throw ApiException.Validation("Verification code is invalid or expired")
        accounts.confirmEmailVerification(userId, hash(token), clock.instant())
            ?: throw ApiException.Validation("Verification code is invalid or expired")
    }
    private fun hash(token: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8)))
}
