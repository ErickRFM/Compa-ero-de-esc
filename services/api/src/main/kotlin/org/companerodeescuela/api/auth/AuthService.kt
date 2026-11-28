package org.companerodeescuela.api.auth

import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.institutions.InMemoryInstitutionRepository
import org.companerodeescuela.api.institutions.InstitutionRepository
import org.companerodeescuela.api.integrations.identity.IdentityProvider
import org.companerodeescuela.api.integrations.identity.InstitutionalCredentials
import org.companerodeescuela.api.invitations.InvitationRepository
import org.companerodeescuela.api.invitations.InvitationStatus
import org.companerodeescuela.api.invitations.InvitationToken
import org.companerodeescuela.api.mail.UnavailableVerificationEmailGateway
import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.LoginRequest
import org.companerodeescuela.shared.contracts.LoginResponse
import org.companerodeescuela.shared.contracts.RefreshSessionRequest
import org.companerodeescuela.shared.contracts.RegisterRequest
import org.companerodeescuela.shared.contracts.RegistrationAccountType
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Locale
import java.util.UUID

/**
 * Authentication use case.
 *
 * Compañero accounts are first-class and work without any institutional API.
 * Institutional identity remains an optional fallback for users who have not
 * created a native account yet.
 */
private data class OwnerInvitationAuthorization(
    val digest: String,
    val expiresAt: Instant,
)

class AuthService(
    private val identityProvider: IdentityProvider,
    private val tokenService: AuthTokenService,
    private val sessions: RefreshSessionRepository,
    private val accounts: PlatformAccountRepository = InMemoryPlatformAccountRepository(),
    private val passwordHasher: PasswordHasher = PasswordHasher(),
    private val clock: Clock = Clock.systemUTC(),
    private val random: SecureRandom = SecureRandom(),
    private val institutions: InstitutionRepository = InMemoryInstitutionRepository(),
    private val verification: AccountVerificationService = AccountVerificationService(accounts, UnavailableVerificationEmailGateway),
    private val refreshRotationGrace: Duration = Duration.ofSeconds(60),
    private val maximumRefreshSessionAge: Duration = Duration.ofDays(90),
    sessionAuthority: PlatformSessionAuthority? = null,
    private val ownerInviteEnvironment: (String) -> String? = System::getenv,
    private val ownerInvitationRepository: OwnerInvitationRepository = InMemoryOwnerInvitationRepository(),
) {
    init {
        require(!refreshRotationGrace.isNegative && !refreshRotationGrace.isZero)
        require(!maximumRefreshSessionAge.isNegative && !maximumRefreshSessionAge.isZero)
    }

    private val authority = sessionAuthority ?: PlatformSessionAuthority(accounts, identityProvider, clock)
    private var invitations: InvitationRepository? = null

    internal fun withInvitations(repository: InvitationRepository?): AuthService =
        apply {
            this.invitations = repository
        }

    suspend fun register(request: RegisterRequest): LoginResponse {
        val displayName = request.displayName.trim()
        val email = request.email.trim().lowercase()

        if (displayName.length !in 2..80 || displayName.any { it.isISOControl() }) {
            throw ApiException.Validation("Display name must contain between 2 and 80 characters")
        }
        if (!EMAIL_REGEX.matches(email) ||
            email.length > 128 ||
            email.substringBefore('@').length > 64 ||
            email.substringBefore('@').let { it.startsWith('.') || it.endsWith('.') || ".." in it } ||
            email.substringAfter('@').split('.').any { it.length !in 1..63 || it.startsWith('-') || it.endsWith('-') }
        ) {
            throw ApiException.Validation("A valid email is required")
        }
        if (request.password.length !in 8..256 || request.password.any { it.isISOControl() }) {
            throw ApiException.Validation("Password must contain between 8 and 256 characters")
        }

        fun bounded(
            value: String?,
            maximum: Int,
            field: String,
        ): String? {
            val cleaned = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            if (cleaned.length > maximum || cleaned.any { it.isISOControl() }) throw ApiException.Validation("Invalid $field")
            return cleaned
        }
        var ownerInvitationAuthorization: OwnerInvitationAuthorization? = null
        if (request.accountType == RegistrationAccountType.OWNER_INVITED) {
            ownerInvitationAuthorization = authorizeOwnerInvitation(email, request.ownerInvitationCode)
        } else if (request.ownerInvitationCode != null) {
            throw ApiException.Validation("Owner invitation code is only valid for owner enrollment")
        }
        if (request.accountType != RegistrationAccountType.INVITED &&
            (request.invitationId != null || request.invitationCode != null)
        ) {
            throw ApiException.Validation("Invitation credentials are only valid for invited enrollment")
        }
        val institutionPreference = bounded(request.requestedInstitutionId, 128, "institution")
        val reference = bounded(request.identityReference, 160, "identity reference")
        val groupPreference = bounded(request.preferredGroup, 128, "group preference")
        var invitedInstitutionId: String? = null
        var pendingInvitationId: String? = null
        var pendingInvitationTokenHash: String? = null
        if (request.accountType == RegistrationAccountType.INVITED) {
            if (institutionPreference != null || groupPreference != null) {
                throw ApiException.Validation("Invited registration uses the invitation institution")
            }
            val invId = request.invitationId?.trim()
            val invCode = request.invitationCode?.trim()
            if (invId == null ||
                invCode == null ||
                !Regex("[A-Za-z0-9_-]{43}").matches(invId) ||
                !Regex("[A-Za-z0-9_-]{43}").matches(invCode)
            ) {
                throw ApiException.Validation("Invitation is invalid or unavailable")
            }
            val invitationRepo =
                invitations ?: throw ApiException.DependencyUnavailable("Invitations are disabled in this environment")
            val record = authenticationDependency { invitationRepo.find(invId) }
            val now = clock.instant()
            val computedHash = InvitationToken.hash(invCode)
            val validInvitation =
                record != null &&
                    MessageDigest.isEqual(
                        record.tokenHash.toByteArray(Charsets.US_ASCII),
                        computedHash.toByteArray(Charsets.US_ASCII),
                    ) &&
                    record.recipient == email &&
                    record.status == InvitationStatus.SENT &&
                    now < record.expiresAt &&
                    authenticationDependency { institutions.findEnabled(record.institutionId) } != null
            if (!validInvitation) {
                throw ApiException.Validation("Invitation is invalid or unavailable")
            }
            val verifiedRecord = requireNotNull(record)
            invitedInstitutionId = verifiedRecord.institutionId
            pendingInvitationId = verifiedRecord.id
            pendingInvitationTokenHash = verifiedRecord.tokenHash
        }
        if (request.accountType in setOf(RegistrationAccountType.TEACHER, RegistrationAccountType.TUTOR) &&
            (institutionPreference == null || reference == null)
        ) {
            throw ApiException.Validation("Institution and identity reference are required")
        }
        if (institutionPreference != null && authenticationDependency { institutions.findEnabled(institutionPreference) } == null) {
            throw ApiException.Validation("Institution is not available for registration")
        }
        if (request.accountType in setOf(RegistrationAccountType.PARTICIPANT, RegistrationAccountType.OWNER_INVITED) &&
            (institutionPreference != null || reference != null || groupPreference != null)
        ) {
            throw ApiException.Validation("Participant registration does not request academic scope")
        }
        ownerInvitationAuthorization?.let { invitation ->
            val existing = authenticationDependency { accounts.findByIdentifier(email) }
            if (existing != null) return convertStudentToOwnerInvitation(existing, request.password, invitation)
        }
        val account =
            PlatformAccount(
                id = UUID.randomUUID().toString(),
                displayName = displayName,
                email = email,
                passwordHash = passwordHasher.hashAsync(request.password),
                roles = emptySet(),
                accountStatus = AccountStatus.PENDING_VERIFICATION,
                registrationIntent =
                    RegistrationIntent(
                        profile = request.accountType,
                        requestedInstitutionId = invitedInstitutionId ?: institutionPreference,
                        identityReference = reference,
                        preferredGroup = groupPreference,
                        pendingInvitationId = pendingInvitationId,
                        pendingInvitationTokenHash = pendingInvitationTokenHash,
                    ),
                createdAt = clock.instant(),
            )
        ownerInvitationAuthorization?.let { invitation ->
            // Consumption is deliberately fail-closed. If account persistence or verification delivery
            // fails afterward, issue a fresh random invitation; never reopen this digest.
            val consumed =
                authenticationDependency {
                    ownerInvitationRepository.consume(
                        invitation.digest,
                        email,
                        invitation.expiresAt,
                        clock.instant(),
                        account.id,
                    )
                }
            if (!consumed) throw ApiException.Forbidden("Owner registration invitation is unavailable")
        }
        if (!authenticationDependency { accounts.create(account) }) {
            throw ApiException.Conflict("An account with that email already exists")
        }
        val delivery = verification.issue(account.id)
        return createSession(account.toUserSummary().copy(verificationDelivery = delivery.status), SessionIdentitySource.NATIVE)
    }

    private fun authorizeOwnerInvitation(
        email: String,
        code: String?,
    ): OwnerInvitationAuthorization {
        val expectedEmail = ownerInviteEnvironment("OWNER_INVITE_EMAIL")?.trim()?.lowercase(Locale.ROOT)
        // Keep request-time checks aligned with SettingsLoader, which trims these environment values.
        val digest = ownerInviteEnvironment("OWNER_INVITE_CODE_SHA256")?.trim()
        val expiry = ownerInviteEnvironment("OWNER_INVITE_EXPIRES_AT")?.trim()?.toLongOrNull()
        val nowSeconds = clock.instant().epochSecond
        val allowed =
            ownerInviteEnvironment("APP_ENV")?.trim()?.lowercase(Locale.ROOT) in setOf("prod", "production") &&
                ownerInviteEnvironment("MONGODB_DATABASE")?.trim() == "companero_dev" &&
                ownerInviteEnvironment("PROD_OWNER_ISOLATION_CERTIFIED") == "true" &&
                expectedEmail != null &&
                email == expectedEmail &&
                expiry != null &&
                expiry > nowSeconds &&
                expiry <= nowSeconds + 7 * 24 * 60 * 60 &&
                digest?.matches(Regex("[a-f0-9]{64}")) == true &&
                code != null &&
                code.matches(Regex("[A-Za-z0-9_-]{43}")) &&
                MessageDigest.isEqual(
                    MessageDigest.getInstance("SHA-256").digest(code.toByteArray(Charsets.UTF_8)),
                    digest.chunked(2).map { it.toInt(16).toByte() }.toByteArray(),
                )
        if (!allowed) throw ApiException.Forbidden("Owner registration invitation is unavailable")
        return OwnerInvitationAuthorization(
            requireNotNull(digest),
            Instant.ofEpochSecond(requireNotNull(expiry)),
        )
    }

    private suspend fun convertStudentToOwnerInvitation(
        existing: PlatformAccount,
        password: String,
        invitation: OwnerInvitationAuthorization,
    ): LoginResponse {
        val operationId = "owner-invite-conversion-${invitation.digest}"
        val exactRetryStatus =
            when (existing.accountStatus) {
                AccountStatus.PENDING_VERIFICATION -> existing.emailVerifiedAt == null
                AccountStatus.ACTIVE -> existing.emailVerifiedAt != null
                else -> false
            }
        val exactRetry =
            existing.registrationIntent?.profile == RegistrationAccountType.OWNER_INVITED &&
                existing.identityAudit.any {
                    it.operationId == operationId &&
                        it.actorId == existing.id &&
                        it.scopeDigest == invitation.digest &&
                        it.registrationProfileBefore == RegistrationAccountType.STUDENT &&
                        it.registrationProfileAfter == RegistrationAccountType.OWNER_INVITED &&
                        it.fromStatus == existing.accountStatus &&
                        it.toStatus == existing.accountStatus &&
                        it.rolesAfter == existing.roles
                } &&
                exactRetryStatus
        val eligiblePending =
            existing.permitsSession &&
                existing.accountStatus == AccountStatus.PENDING_VERIFICATION &&
                existing.emailVerifiedAt == null &&
                existing.registrationIntent == RegistrationIntent(RegistrationAccountType.STUDENT) &&
                existing.roles.isEmpty() &&
                existing.institutionId == null &&
                existing.roleApplications.isEmpty() &&
                existing.adminCapabilities.isEmpty() &&
                existing.identityAudit.isEmpty()
        val eligibleVerifiedStudent =
            existing.permitsSession &&
                existing.accountStatus == AccountStatus.ACTIVE &&
                existing.emailVerifiedAt != null &&
                existing.registrationIntent == RegistrationIntent(RegistrationAccountType.STUDENT) &&
                existing.roles == setOf(UserRole.STUDENT) &&
                existing.institutionId == null &&
                existing.roleApplications.isEmpty() &&
                existing.adminCapabilities.isEmpty() &&
                existing.identityAudit.singleOrNull()?.let { audit ->
                    audit.operationId.startsWith("email-verification-") &&
                        audit.actorId == existing.id &&
                        audit.fromStatus == AccountStatus.PENDING_VERIFICATION &&
                        audit.toStatus == AccountStatus.ACTIVE &&
                        audit.rolesBefore.isEmpty() &&
                        audit.rolesAfter == setOf(UserRole.STUDENT) &&
                        audit.institutionBefore == null &&
                        audit.institutionAfter == null &&
                        audit.registrationProfileBefore == RegistrationAccountType.STUDENT &&
                        audit.registrationProfileAfter == RegistrationAccountType.STUDENT &&
                        audit.scopeDigest == null &&
                        audit.approvedApplicationId == null
                } == true
        val eligible = eligiblePending || eligibleVerifiedStudent
        if (!exactRetry && !eligible) throw ApiException.Conflict("An account with that email already exists")
        if (!passwordHasher.verifyAsync(password, existing.passwordHash)) {
            throw ApiException.Conflict("An account with that email already exists")
        }
        val now = clock.instant()
        if (existing.accountStatus == AccountStatus.PENDING_VERIFICATION) {
            val recentVerificationSends =
                existing.emailVerification
                    ?.recentIssuedAt
                    .orEmpty()
                    .count { it > now.minusSeconds(3600) }
            if (existing.emailVerification?.issuedAt?.let { now < it.plusSeconds(60) } == true ||
                recentVerificationSends >= 3
            ) {
                throw ApiException.RateLimited("Wait before requesting another verification email")
            }
        }
        if (!exactRetry) {
            val consumed =
                authenticationDependency {
                    ownerInvitationRepository.consume(
                        invitation.digest,
                        existing.email,
                        invitation.expiresAt,
                        clock.instant(),
                        existing.id,
                    )
                }
            if (!consumed) throw ApiException.Forbidden("Owner registration invitation is unavailable")
            val updated =
                authenticationDependency {
                    accounts.changeIdentity(
                        existing.id,
                        existing.authRevision,
                        AccountIdentityChange(
                            operationId = operationId,
                            actorId = existing.id,
                            accountStatus = existing.accountStatus,
                            roles = existing.roles,
                            institutionId = existing.institutionId,
                            occurredAt = now,
                            scopeDigest = invitation.digest,
                            registrationIntent = RegistrationIntent(RegistrationAccountType.OWNER_INVITED),
                        ),
                    )
                } ?: throw ApiException.Conflict("Owner registration could not be completed; request a new invitation")
            return if (updated.emailVerifiedAt == null) {
                val delivery = verification.issue(updated.id)
                createSession(
                    updated.toUserSummary().copy(verificationDelivery = delivery.status),
                    SessionIdentitySource.NATIVE,
                )
            } else {
                createSession(updated.toUserSummary(), SessionIdentitySource.NATIVE)
            }
        }
        return if (existing.emailVerifiedAt == null) {
            val delivery = verification.issue(existing.id)
            createSession(
                existing.toUserSummary().copy(verificationDelivery = delivery.status),
                SessionIdentitySource.NATIVE,
            )
        } else {
            createSession(existing.toUserSummary(), SessionIdentitySource.NATIVE)
        }
    }

    suspend fun verifyEmail(
        userId: String,
        token: String,
    ): LoginResponse {
        val verified = verification.verify(userId, token)
        val intent = verified.registrationIntent
        val invitationRepo = invitations
        val invitationAccepted =
            when {
                invitationRepo == null -> false
                intent?.profile == RegistrationAccountType.INVITED &&
                    intent.pendingInvitationId != null &&
                    intent.pendingInvitationTokenHash != null -> {
                    authenticationDependency {
                        invitationRepo.accept(
                            id = intent.pendingInvitationId,
                            tokenHash = intent.pendingInvitationTokenHash,
                            accountEmail = verified.email,
                            emailVerified = true,
                            accountId = verified.id,
                            operationId = "register-verify-${verified.id}",
                            now = verified.emailVerifiedAt ?: clock.instant(),
                        )
                    } != null
                }
                verified.accountStatus == AccountStatus.PENDING_APPROVAL ->
                    authenticationDependency { invitationRepo.hasAcceptedForAccount(verified.id) }
                else -> false
            }
        return createSession(
            verified.toUserSummary().copy(invitationAccepted = invitationAccepted),
            SessionIdentitySource.NATIVE,
        )
    }

    suspend fun login(request: LoginRequest): LoginResponse {
        if (request.username.length > 128 || request.username.any(Char::isISOControl)) {
            throw ApiException.Validation("Username must be at most 128 characters and contain no control characters")
        }
        val username = request.username.trim()
        if (username.isBlank()) {
            throw ApiException.Validation("Username is required")
        }
        if (request.password.isEmpty()) {
            throw ApiException.Validation("Password is required")
        }

        val localAccount = authenticationDependency { accounts.findByIdentifier(username) }
        if (localAccount != null) {
            val verification = passwordHasher.verifyAndUpgradeAsync(request.password, localAccount.passwordHash)
            if (!localAccount.permitsSession || !verification.verified) {
                throw ApiException.Unauthorized("Invalid username or password")
            }
            verification.upgradedHash?.let {
                authenticationDependency { accounts.updatePasswordHash(localAccount.id, localAccount.passwordHash, it) }
            }
            val invitationAccepted =
                invitations?.let { repo ->
                    if (localAccount.accountStatus == AccountStatus.PENDING_APPROVAL) {
                        authenticationDependency { repo.hasAcceptedForAccount(localAccount.id) }
                    } else {
                        false
                    }
                } ?: false
            return createSession(
                localAccount.toUserSummary().copy(invitationAccepted = invitationAccepted),
                SessionIdentitySource.NATIVE,
            )
        }

        passwordHasher.verifyAsync(request.password, DUMMY_PASSWORD_HASH)
        val institutional =
            authenticationDependency {
                identityProvider.authenticate(
                    InstitutionalCredentials(
                        username = username,
                        password = request.password,
                    ),
                )
            } ?: throw ApiException.Unauthorized("Invalid username or password")

        return createSession(
            UserSummary(
                id = institutional.externalId,
                displayName = institutional.displayName,
                email = institutional.email,
                roles = institutional.roles,
                active = true,
            ),
            SessionIdentitySource.INSTITUTIONAL,
        )
    }

    suspend fun refresh(request: RefreshSessionRequest): LoginResponse {
        if (request.sessionId.isBlank() || request.refreshToken.isBlank()) {
            throw ApiException.Unauthorized()
        }
        val now = clock.instant()
        val session =
            authenticationDependency { sessions.find(request.sessionId) }
                ?.takeIf {
                    it.revokedAt == null && it.expiresAt > now && now.isBefore(it.createdAt.plus(maximumRefreshSessionAge))
                }
                ?: throw ApiException.Unauthorized()

        val presentedHash = hashRefreshToken(request.refreshToken)
        val currentHashMatches =
            MessageDigest.isEqual(
                session.tokenHash.toByteArray(Charsets.UTF_8),
                presentedHash.toByteArray(Charsets.UTF_8),
            )
        val previousHashInGrace =
            session.immediatelyPreviousTokenHash == presentedHash &&
                session.lastRotatedAt?.plus(refreshRotationGrace)?.let { !now.isAfter(it) } == true
        if (!currentHashMatches && !previousHashInGrace) {
            authenticationDependency { sessions.revoke(session.id, presentedHash, now) }
            throw ApiException.Unauthorized()
        }

        val baseUser = authority.currentUser(session) ?: throw ApiException.Unauthorized()
        val updatedUser =
            if (baseUser.accountStatus == AccountStatus.PENDING_APPROVAL && invitations != null) {
                baseUser.copy(invitationAccepted = authenticationDependency { invitations!!.hasAcceptedForAccount(baseUser.id) })
            } else {
                baseUser
            }

        val nextRefreshToken = newRefreshToken()
        val rotated =
            authenticationDependency {
                sessions.rotate(
                    sessionId = session.id,
                    currentTokenHash = presentedHash,
                    nextTokenHash = hashRefreshToken(nextRefreshToken),
                    user = updatedUser,
                    now = now,
                    expiresAt = minOf(now.plus(REFRESH_TOKEN_TTL), session.createdAt.plus(maximumRefreshSessionAge)),
                    rotationGrace = refreshRotationGrace,
                )
            } ?: throw ApiException.Unauthorized()

        return response(rotated, nextRefreshToken)
    }

    suspend fun logout(request: RefreshSessionRequest) {
        if (request.sessionId.isBlank() || request.refreshToken.isBlank()) return
        authenticationDependency {
            sessions.revoke(
                sessionId = request.sessionId,
                presentedTokenHash = hashRefreshToken(request.refreshToken),
                now = clock.instant(),
            )
        }
    }

    private suspend fun createSession(
        user: UserSummary,
        source: SessionIdentitySource,
    ): LoginResponse {
        val now = clock.instant()
        val sessionId = UUID.randomUUID().toString()
        val refreshToken = newRefreshToken()
        val pending =
            RefreshSession(
                id = sessionId,
                tokenHash = hashRefreshToken(refreshToken),
                user = user,
                createdAt = now,
                expiresAt = minOf(now.plus(REFRESH_TOKEN_TTL), now.plus(maximumRefreshSessionAge)),
                identitySource = source,
            )
        val liveUser = authority.currentUser(pending) ?: throw ApiException.Unauthorized()
        val currentUser = if (user.invitationAccepted) liveUser.copy(invitationAccepted = true) else liveUser
        val session = pending.copy(user = currentUser)
        authenticationDependency { sessions.create(session) }
        return response(session, refreshToken)
    }

    private fun response(
        session: RefreshSession,
        refreshToken: String,
    ): LoginResponse {
        val accessToken = tokenService.issue(session.user, session.id, session.generation)
        return LoginResponse(
            accessToken = accessToken.value,
            expiresAtEpochSeconds = accessToken.expiresAtEpochSeconds,
            sessionId = session.id,
            refreshToken = refreshToken,
            user = session.user,
        )
    }

    private fun newRefreshToken(): String {
        val bytes = ByteArray(REFRESH_TOKEN_BYTES)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hashRefreshToken(token: String): String =
        Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(token.toByteArray()))

    private companion object {
        const val DUMMY_PASSWORD_HASH = "pbkdf2-sha256:600000:AAECAwQFBgcICQoLDA0ODw:yq8_JTOd2RABK-PjQsVxm5wklWj_j4ZQ2eTaDf-qxDI"
        val REFRESH_TOKEN_TTL: Duration = Duration.ofDays(30)
        const val REFRESH_TOKEN_BYTES = 32
        val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9\\-]+(?:\\.[A-Za-z0-9\\-]+)+$")
    }
}
