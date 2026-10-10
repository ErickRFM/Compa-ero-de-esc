package org.companerodeescuela.api.auth

import java.time.Instant
import java.util.UUID
import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.RegistrationAccountType
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.VerificationDeliveryStatus

/** Submitted preferences and identity reference, never a granted role or tenant. */
data class RegistrationIntent(val profile: RegistrationAccountType, val requestedInstitutionId: String? = null,
    val identityReference: String? = null, val preferredGroup: String? = null) {
    override fun toString() = "RegistrationIntent(profile=$profile, identityReferencePresent=${identityReference != null})"
}
data class EmailVerificationChallenge(val tokenHash: String, val issuedAt: Instant, val expiresAt: Instant,
    val authRevision: Long, val recentIssuedAt: List<Instant>,
    val deliveryStatus: VerificationDeliveryStatus = VerificationDeliveryStatus.PENDING) {
    override fun toString() = "EmailVerificationChallenge(expiresAt=$expiresAt, status=$deliveryStatus)"
}

internal fun PlatformAccount.withEmailChallenge(expectedRevision: Long, hash: String, now: Instant, expiresAt: Instant): PlatformAccount? {
    if (!active || accountStatus != AccountStatus.PENDING_VERIFICATION || emailVerifiedAt != null ||
        registrationIntent == null || authRevision != expectedRevision || expectedRevision < 0 ||
        !Regex("[A-Za-z0-9_-]{43}").matches(hash) || expiresAt <= now || expiresAt > now.plusSeconds(3600)) return null
    val history = emailVerification?.recentIssuedAt.orEmpty().filter { it > now.minusSeconds(3600) }
    if (emailVerification?.issuedAt?.let { now < it.plusSeconds(60) } == true || history.size >= 3) return null
    return copy(emailVerification = EmailVerificationChallenge(hash, now, expiresAt, authRevision, history + now))
}

internal fun PlatformAccount.withVerifiedEmail(hash: String, now: Instant): PlatformAccount? {
    val challenge = emailVerification ?: return null
    val intent = registrationIntent ?: return null
    if (!active || accountStatus != AccountStatus.PENDING_VERIFICATION || emailVerifiedAt != null ||
        challenge.authRevision != authRevision || challenge.expiresAt <= now || challenge.issuedAt > now ||
        challenge.tokenHash != hash) return null
    val roles = when (intent.profile) {
        RegistrationAccountType.STUDENT -> setOf(UserRole.STUDENT)
        RegistrationAccountType.PARTICIPANT -> setOf(UserRole.WORKSHOP_PARTICIPANT)
        RegistrationAccountType.TEACHER, RegistrationAccountType.TUTOR -> emptySet()
    }
    val status = if (intent.profile in setOf(RegistrationAccountType.STUDENT, RegistrationAccountType.PARTICIPANT))
        AccountStatus.ACTIVE else AccountStatus.PENDING_APPROVAL
    return applyIdentityChange(authRevision, AccountIdentityChange("email-verification-${UUID.randomUUID()}", id,
        status, roles, institutionId, now))?.copy(emailVerifiedAt = now, emailVerification = null)
}
