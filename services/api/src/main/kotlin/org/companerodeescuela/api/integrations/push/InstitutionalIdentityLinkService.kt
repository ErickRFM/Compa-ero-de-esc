package org.companerodeescuela.api.integrations.push

import org.companerodeescuela.api.admin.InstitutionScopePolicy
import org.companerodeescuela.api.auth.PlatformAccount
import org.companerodeescuela.api.auth.PlatformAccountRepository
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.institutions.InstitutionRepository
import org.companerodeescuela.api.security.MfaService
import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.AdminCapability
import org.companerodeescuela.shared.contracts.InstitutionalIdentityKind
import org.companerodeescuela.shared.contracts.InstitutionalIdentityLinkDigest
import org.companerodeescuela.shared.contracts.InstitutionalIdentityLinkRequest
import org.companerodeescuela.shared.contracts.InstitutionalIdentityLinkRevokeRequest
import org.companerodeescuela.shared.contracts.InstitutionalIdentityLinkSummary
import org.companerodeescuela.shared.contracts.StepUpAction
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary
import java.time.Clock

class InstitutionalIdentityLinkService(
    private val accounts: PlatformAccountRepository,
    private val institutions: InstitutionRepository,
    private val repository: InstitutionalIdentityLinkRepository,
    private val mfa: MfaService,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val policy = InstitutionScopePolicy(accounts, clock)

    suspend fun link(
        actor: UserSummary,
        request: InstitutionalIdentityLinkRequest,
    ): InstitutionalIdentityLinkSummary {
        validate(request)
        val current = policy.require(actor, AdminCapability.MANAGE_ACADEMICS, request.institutionId)
        if (institutions.findEnabled(request.institutionId) == null) {
            throw ApiException.Validation("An enabled institution is required")
        }

        val digest = InstitutionalIdentityLinkDigest.command(request)
        val prior = repository.findByExternalId(request.institutionId, request.entityType, request.externalId)
        if (prior != null) {
            if (prior.nativeUserId == request.nativeUserId &&
                prior.linkedBy == current.id &&
                prior.operationId == request.operationId &&
                prior.payloadDigest == digest
            ) {
                return prior.toSummary()
            }
            throw ApiException.Conflict("Institutional identifier is already linked")
        }

        mfa.requireStepUp(
            current,
            request.proof,
            StepUpAction.MANAGE_ACADEMICS,
            request.institutionId,
            request.operationId,
            digest,
        )
        val target =
            accounts.findById(request.nativeUserId)
                ?: throw ApiException.Validation("An existing native account is required")
        validateTarget(target, request)

        val link =
            InstitutionalIdentityLink(
                institutionId = request.institutionId,
                entityType = request.entityType,
                externalId = request.externalId,
                nativeUserId = target.id,
                linkedBy = current.id,
                operationId = request.operationId,
                payloadDigest = digest,
                linkedAt = clock.instant(),
            )
        return when (repository.createIfAbsent(link)) {
            InstitutionalIdentityLinkAcceptance.CREATED,
            InstitutionalIdentityLinkAcceptance.DUPLICATE,
            -> link.toSummary()
            InstitutionalIdentityLinkAcceptance.CONFLICT ->
                throw ApiException.Conflict("Institutional identity changed concurrently or is already linked")
        }
    }

    /**
     * Revokes an active link (audit A3). A wrong link used to be permanent and blocked the right account;
     * academic reads stop immediately because resolve() and the SIII authority both require an active link.
     */
    suspend fun revoke(
        actor: UserSummary,
        request: InstitutionalIdentityLinkRevokeRequest,
    ): InstitutionalIdentityLinkSummary {
        listOf(request.institutionId, request.operationId).forEach { value ->
            if (value.isBlank() || value != value.trim() || value.length > 128 || value.any(Char::isISOControl)) {
                throw ApiException.Validation("Bounded institution and operation identifiers are required")
            }
        }
        if (request.externalId.isBlank() ||
            request.externalId != request.externalId.trim() ||
            request.externalId.length > 256 ||
            request.externalId.any(Char::isISOControl)
        ) {
            throw ApiException.Validation("A bounded institutional identifier is required")
        }
        val reason = request.reason.trim()
        if (reason.length !in 3..500 || reason.any { it.isISOControl() && it != '\n' }) {
            throw ApiException.Validation("A revocation reason between 3 and 500 characters is required")
        }
        val current = policy.require(actor, AdminCapability.MANAGE_ACADEMICS, request.institutionId)
        repository.findByExternalId(request.institutionId, request.entityType, request.externalId)
            ?: throw ApiException.NotFound("Institutional identity link is not active")
        mfa.requireStepUp(
            current,
            request.proof,
            StepUpAction.MANAGE_ACADEMICS,
            request.institutionId,
            request.operationId,
            InstitutionalIdentityLinkDigest.revoke(request),
        )
        val revoked =
            repository.revoke(
                request.institutionId,
                request.entityType,
                request.externalId,
                InstitutionalIdentityLinkRevocation(current.id, reason, request.operationId, clock.instant()),
            ) ?: throw ApiException.Conflict("Institutional identity link changed concurrently")
        return revoked.toSummary()
    }

    /** Dedicated institution-owned archival export only; never authorizes mobile or live academic operations. */
    internal suspend fun historicalStudentId(
        institutionId: String,
        externalId: String,
    ): String? = repository.findByExternalId(institutionId, InstitutionalIdentityKind.STUDENT, externalId)?.nativeUserId

    internal suspend fun historicalTeacherId(
        institutionId: String,
        externalId: String,
    ): String? = repository.findByExternalId(institutionId, InstitutionalIdentityKind.TEACHER, externalId)?.nativeUserId

    internal suspend fun historicalTeacherExternalId(
        institutionId: String,
        nativeId: String,
    ): String? = repository.findByNativeUserId(institutionId, InstitutionalIdentityKind.TEACHER, nativeId)?.externalId

    /** Internal projector lookup. The returned account is revalidated against current authority. */
    suspend fun resolve(
        institutionId: String,
        entityType: InstitutionalIdentityKind,
        externalId: String,
    ): PlatformAccount? {
        val link = repository.findByExternalId(institutionId, entityType, externalId) ?: return null
        val account = accounts.findById(link.nativeUserId) ?: return null
        return account.takeIf {
            it.permitsSession &&
                it.accountStatus == AccountStatus.ACTIVE &&
                it.emailVerifiedAt?.let { verifiedAt -> verifiedAt <= clock.instant() } == true &&
                it.institutionId == institutionId &&
                roleFor(entityType) in it.roles
        }
    }

    private fun validateTarget(
        target: PlatformAccount,
        request: InstitutionalIdentityLinkRequest,
    ) {
        if (!target.permitsSession ||
            target.accountStatus != AccountStatus.ACTIVE ||
            target.emailVerifiedAt == null ||
            target.emailVerifiedAt > clock.instant() ||
            target.institutionId != request.institutionId ||
            roleFor(request.entityType) !in target.roles ||
            target.roles.any { it == UserRole.ADMIN || it == UserRole.SUPER_ADMIN }
        ) {
            throw ApiException.Validation(
                "A verified active native account with the matching institution and role is required",
            )
        }
    }

    private fun validate(request: InstitutionalIdentityLinkRequest) {
        listOf(request.institutionId, request.nativeUserId, request.operationId).forEach { value ->
            if (value.isBlank() ||
                value != value.trim() ||
                value.length > 128 ||
                value.any(Char::isISOControl)
            ) {
                throw ApiException.Validation(
                    "Bounded institution, account and operation identifiers are required",
                )
            }
        }
        if (request.externalId.isBlank() ||
            request.externalId != request.externalId.trim() ||
            request.externalId.length > 256 ||
            request.externalId.any(Char::isISOControl)
        ) {
            throw ApiException.Validation("A bounded institutional identifier is required")
        }
    }

    private fun roleFor(kind: InstitutionalIdentityKind) =
        when (kind) {
            InstitutionalIdentityKind.STUDENT -> UserRole.STUDENT
            InstitutionalIdentityKind.TEACHER -> UserRole.TEACHER
            InstitutionalIdentityKind.COORDINATOR -> UserRole.COORDINATOR
        }

    private fun InstitutionalIdentityLink.toSummary() =
        InstitutionalIdentityLinkSummary(
            institutionId = institutionId,
            entityType = entityType,
            externalId = externalId,
            nativeUserId = nativeUserId,
            linkedBy = linkedBy,
            linkedAtEpochSeconds = linkedAt.epochSecond,
        )
}
