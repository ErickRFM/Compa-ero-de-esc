package org.companerodeescuela.api.auth

import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.RegistrationAccountType
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.VerificationDeliveryStatus
import java.time.Instant
import java.util.UUID

/** Submitted preferences and identity reference, never a granted role or tenant. */
data class RegistrationIntent(
    val profile: RegistrationAccountType,
    val requestedInstitutionId: String? = null,
    val identityReference: String? = null,
    val preferredGroup: String? = null,
) {
    override fun toString() = "RegistrationIntent(profile=$profile, identityReferencePresent=${identityReference != null})"
}

data class EmailVerificationChallenge(
    val tokenHash: String,
    val issuedAt: Instant,
    val expiresAt: Instant,
    val authRevision: Long,
    val recentIssuedAt: List<Instant>,
    val deliveryStatus: VerificationDeliveryStatus = VerificationDeliveryStatus.PENDING,
) {
    override fun toString() = "EmailVerificationChallenge(expiresAt=$expiresAt, status=$deliveryStatus)"
}

internal fun PlatformAccount.withEmailChallenge(
    expectedRevision: Long,
    hash: String,
    now: Instant,
    expiresAt: Instant,
): PlatformAccount? {
    if (!permitsSession ||
        accountStatus !in setOf(AccountStatus.PENDING_VERIFICATION, AccountStatus.ACTIVE) ||
        emailVerifiedAt != null ||
        (accountStatus == AccountStatus.PENDING_VERIFICATION && registrationIntent == null) ||
        authRevision != expectedRevision ||
        expectedRevision < 0 ||
        !Regex("[A-Za-z0-9_-]{43}").matches(hash) ||
        expiresAt <= now ||
        expiresAt > now.plusSeconds(3600)
    ) {
        return null
    }
    val history = emailVerification?.recentIssuedAt.orEmpty().filter { it > now.minusSeconds(3600) }
    if (emailVerification?.issuedAt?.let { now < it.plusSeconds(60) } == true || history.size >= 3) return null
    return copy(emailVerification = EmailVerificationChallenge(hash, now, expiresAt, authRevision, history + now))
}

internal fun PlatformAccount.withVerifiedEmail(
    hash: String,
    now: Instant,
): PlatformAccount? {
    val challenge = emailVerification ?: return null
    val intent = registrationIntent
    if (!permitsSession ||
        accountStatus !in setOf(AccountStatus.PENDING_VERIFICATION, AccountStatus.ACTIVE) ||
        (accountStatus == AccountStatus.PENDING_VERIFICATION && intent == null) ||
        emailVerifiedAt != null ||
        challenge.authRevision != authRevision ||
        challenge.expiresAt <= now ||
        challenge.issuedAt > now ||
        challenge.tokenHash != hash
    ) {
        return null
    }
    val roles =
        if (accountStatus == AccountStatus.ACTIVE) {
            roles
        } else {
            when (requireNotNull(intent).profile) {
                RegistrationAccountType.STUDENT -> setOf(UserRole.STUDENT)
                RegistrationAccountType.PARTICIPANT -> setOf(UserRole.WORKSHOP_PARTICIPANT)
                RegistrationAccountType.TEACHER, RegistrationAccountType.TUTOR -> emptySet()
            }
        }
    val status =
        if (accountStatus == AccountStatus.ACTIVE ||
            requireNotNull(intent).profile in setOf(RegistrationAccountType.STUDENT, RegistrationAccountType.PARTICIPANT)
        ) {
            AccountStatus.ACTIVE
        } else {
            AccountStatus.PENDING_APPROVAL
        }
    return applyIdentityChange(
        authRevision,
        AccountIdentityChange(
            "email-verification-${UUID.randomUUID()}",
            id,
            status,
            roles,
            institutionId,
            now,
        ),
    )?.copy(emailVerifiedAt = now, emailVerification = null)?.let { it.copy(roleApplications = it.staffApplications()) }
}
