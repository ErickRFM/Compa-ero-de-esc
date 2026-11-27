package org.companerodeescuela.api.invitations

import org.companerodeescuela.api.auth.PlatformAccount
import org.companerodeescuela.api.auth.PlatformAccountRepository
import org.companerodeescuela.api.auth.authenticationDependency
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.institutions.InstitutionRepository
import org.companerodeescuela.api.security.MfaService
import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.ApiErrorCode
import org.companerodeescuela.shared.contracts.StepUpAction
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.VerificationDeliveryStatus
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.Base64
import java.util.Locale

/** This never creates accounts or grants roles. A scoped MFA proof is required before sending. */
internal data class InvitationDispatchCommand(
    val recipient: String,
    val institutionId: String,
    val profile: InvitationProfile,
    val operationId: String,
) {
    fun canonical(): InvitationDispatchCommand = copy(recipient = recipient.trim().lowercase(Locale.ROOT))

    fun valid(): Boolean {
        val email = recipient
        return email == email.trim().lowercase(Locale.ROOT) &&
            email.length in 6..128 &&
            email.matches(Regex("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+")) &&
            institutionId.matches(Regex("[A-Za-z0-9_-]{1,128}")) &&
            operationId.matches(Regex("[A-Za-z0-9_-]{8,128}"))
    }

    fun digest(actorId: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(
                listOf("companero.invitation.send.v1", actorId, recipient, institutionId, profile.name, operationId)
                    .joinToString("\n")
                    .toByteArray(Charsets.UTF_8),
            ).joinToString("") { "%02x".format(it) }
}

internal fun interface InvitationEmailGateway {
    suspend fun send(
        recipient: String,
        code: String,
        invitationId: String,
        expiresAt: Instant,
    ): VerificationDeliveryStatus

    suspend fun sendOwnerBootstrap(
        recipient: String,
        code: String,
        invitationId: String,
        expiresAt: Instant,
    ): VerificationDeliveryStatus = send(recipient, code, invitationId, expiresAt)
}

internal enum class InvitationEmailKind { REGISTRATION, OWNER_BOOTSTRAP }

internal object UnavailableInvitationEmailGateway : InvitationEmailGateway {
    override suspend fun send(
        recipient: String,
        code: String,
        invitationId: String,
        expiresAt: Instant,
    ) = VerificationDeliveryStatus.UNAVAILABLE
}

internal data class InvitationPreparation(
    val operationId: String,
    val resourceId: String,
    val payloadDigest: String,
)

internal data class InvitationDispatchResult(
    val invitationId: String,
    val delivery: VerificationDeliveryStatus,
    val expiresAtEpochSeconds: Long,
)

internal class InvitationDispatchService(
    private val accounts: PlatformAccountRepository,
    private val mfa: MfaService,
    private val repository: InvitationRepository,
    private val email: InvitationEmailGateway,
    private val institutions: InstitutionRepository,
    private val outbox: InvitationOutbox? = null,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun prepare(
        actor: PlatformAccount,
        proposed: InvitationDispatchCommand,
    ): InvitationPreparation {
        val command = proposed.canonical()
        if (!command.valid()) throw ApiException.Validation("Invitation details are invalid")
        val current = authorizedIssuer(actor)
        requireInstitution(command)
        return InvitationPreparation(command.operationId, RESOURCE_ID, command.digest(current.id))
    }

    suspend fun dispatch(
        actor: PlatformAccount,
        proposed: InvitationDispatchCommand,
        scopedProof: String,
    ): InvitationDispatchResult {
        val command = proposed.canonical()
        if (!command.valid()) throw ApiException.Validation("Invitation details are invalid")
        val current = authorizedIssuer(actor)
        requireInstitution(command)
        if (!Regex("[A-Za-z0-9_-]{43}").matches(scopedProof)) throw ApiException.Forbidden("MFA proof is required")
        authenticationDependency {
            mfa.requireStepUp(
                current,
                scopedProof,
                StepUpAction.ADMIN_INVITE,
                RESOURCE_ID,
                command.operationId,
                command.digest(current.id),
            )
        }
        val now = clock.instant()
        val expiresAt = now.plusSeconds(7 * 24 * 60 * 60L)
        val token = InvitationToken.generate()
        val id = invitationId(current.id, command.operationId)
        val record =
            InvitationRecord(
                id,
                command.recipient,
                command.institutionId,
                command.profile,
                current.id,
                now,
                expiresAt,
                InvitationStatus.SENT,
                token.hash,
            )
        if (!authenticationDependency { repository.create(record) }) {
            throw ApiException.Conflict("This invitation operation was already processed")
        }
        // Persist encrypted retry capability before network I/O so a process crash cannot strand it.
        if (outbox != null) {
            val queued =
                try {
                    authenticationDependency {
                        outbox.enqueue(
                            OutboxEntry(
                                invitationId = id,
                                recipient = command.recipient,
                                tokenValue = token.value,
                                expiresAt = expiresAt,
                                nextAttemptAt = now.plusSeconds(60),
                                createdAt = now,
                            ),
                        )
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    runCatching { repository.revoke(id, clock.instant()) }
                    throw failure
                }
            if (!queued) {
                authenticationDependency { repository.revoke(id, clock.instant()) }
                throw ApiException.Conflict("Invitation dispatch cannot be queued")
            }
        }
        val delivery =
            try {
                email.send(command.recipient, token.value, id, expiresAt)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                VerificationDeliveryStatus.UNCONFIRMED
            }
        when (delivery) {
            VerificationDeliveryStatus.FAILED, VerificationDeliveryStatus.UNAVAILABLE -> {
                authenticationDependency { repository.revoke(id, clock.instant()) }
                runCatching { outbox?.markFailed(id, clock.instant()) }
            }
            VerificationDeliveryStatus.ACCEPTED -> runCatching { outbox?.clearToken(id) }
            VerificationDeliveryStatus.DELIVERED -> runCatching { outbox?.markDelivered(id, clock.instant()) }
            else -> Unit
        }
        authenticationDependency { repository.updateDeliveryStatus(id, delivery.name, clock.instant()) }
        return InvitationDispatchResult(id, delivery, expiresAt.epochSecond)
    }

    /**
     * Prepares a reproducible, command-bound MFA scope for retrying an existing invitation.
     * The returned operationId and payloadDigest do not depend on server wall-clock time.
     */
    suspend fun prepareRetry(
        actor: PlatformAccount,
        invitationId: String,
        operationId: String,
    ): InvitationPreparation {
        if (!Regex("[A-Za-z0-9_-]{43}").matches(invitationId) ||
            !Regex("[A-Za-z0-9_-]{8,128}").matches(operationId)
        ) {
            throw ApiException.Validation("Invitation retry details are invalid")
        }
        val current = authorizedIssuer(actor)
        val existing =
            authenticationDependency { repository.find(invitationId) }
                ?: throw ApiException.Validation("Invitation is invalid or unavailable")
        if (existing.issuerId != current.id) throw ApiException.Forbidden("Only the original issuer can retry")
        requireInstitutionId(existing.institutionId)
        val now = clock.instant()
        if (now >= existing.expiresAt) throw ApiException.Validation("Invitation has expired")
        if (existing.status !in setOf(InvitationStatus.SENT, InvitationStatus.FAILED)) {
            throw ApiException.Conflict("Invitation cannot be retried in its current state")
        }
        if (operationId in existing.retryOperations) {
            throw ApiException.Conflict("This invitation retry operation was already processed")
        }
        return InvitationPreparation(
            operationId = operationId,
            resourceId = RESOURCE_ID,
            payloadDigest = retryDigest(current.id, existing.id, operationId),
        )
    }

    /**
     * Retries delivery of an existing invitation. Generates a fresh token for replay
     * protection, consumes a single-use MFA proof bound to the client-prepared operationId,
     * prevents operationId replay, and re-sends the email.
     */
    suspend fun retry(
        actor: PlatformAccount,
        invitationId: String,
        operationId: String,
        scopedProof: String,
    ): InvitationDispatchResult {
        val current = authorizedIssuer(actor)
        if (!Regex("[A-Za-z0-9_-]{43}").matches(invitationId) ||
            !Regex("[A-Za-z0-9_-]{8,128}").matches(operationId)
        ) {
            throw ApiException.Validation("Invitation retry details are invalid")
        }
        if (!Regex("[A-Za-z0-9_-]{43}").matches(scopedProof)) throw ApiException.Forbidden("MFA proof is required")
        val existing =
            authenticationDependency { repository.find(invitationId) }
                ?: throw ApiException.Validation("Invitation is invalid or unavailable")
        if (existing.issuerId != current.id) throw ApiException.Forbidden("Only the original issuer can retry")
        requireInstitutionId(existing.institutionId)
        if (operationId in existing.retryOperations) {
            throw ApiException.Conflict("This invitation retry operation was already processed")
        }
        val digest = retryDigest(current.id, existing.id, operationId)
        authenticationDependency {
            mfa.requireStepUp(
                current,
                scopedProof,
                StepUpAction.ADMIN_INVITE,
                RESOURCE_ID,
                operationId,
                digest,
            )
        }
        val now = clock.instant()
        if (now >= existing.expiresAt) throw ApiException.Validation("Invitation has expired")
        val newToken = InvitationToken.generate()
        if (!authenticationDependency { repository.retry(invitationId, newToken.hash, now, operationId) }) {
            throw ApiException.Conflict("Invitation cannot be retried in its current state")
        }
        if (outbox != null) {
            val queued =
                authenticationDependency {
                    outbox.replace(
                        OutboxEntry(
                            invitationId = invitationId,
                            recipient = existing.recipient,
                            tokenValue = newToken.value,
                            expiresAt = existing.expiresAt,
                            nextAttemptAt = now.plusSeconds(60),
                            createdAt = now,
                        ),
                    )
                }
            if (!queued) {
                authenticationDependency { repository.revoke(invitationId, clock.instant()) }
                throw ApiException.Conflict("Invitation retry cannot be queued")
            }
        }
        val delivery =
            try {
                email.send(existing.recipient, newToken.value, invitationId, existing.expiresAt)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                VerificationDeliveryStatus.UNCONFIRMED
            }
        when (delivery) {
            VerificationDeliveryStatus.FAILED, VerificationDeliveryStatus.UNAVAILABLE -> {
                authenticationDependency { repository.revoke(invitationId, clock.instant()) }
                runCatching { outbox?.markFailed(invitationId, clock.instant()) }
            }
            VerificationDeliveryStatus.ACCEPTED -> runCatching { outbox?.clearToken(invitationId) }
            VerificationDeliveryStatus.DELIVERED -> runCatching { outbox?.markDelivered(invitationId, clock.instant()) }
            else -> Unit
        }
        authenticationDependency { repository.updateDeliveryStatus(invitationId, delivery.name, clock.instant()) }
        return InvitationDispatchResult(invitationId, delivery, existing.expiresAt.epochSecond)
    }

    /** Replays only live invitation capabilities whose persisted digest still matches. */
    suspend fun retryPendingOutbox(): Int {
        val queue = outbox ?: return 0
        val now = clock.instant()
        val pending = authenticationDependency { queue.pending(now) }
        for (entry in pending) {
            val invitation = authenticationDependency { repository.find(entry.invitationId) }
            if (invitation == null ||
                invitation.status != InvitationStatus.SENT ||
                invitation.expiresAt <= now ||
                invitation.recipient != entry.recipient ||
                invitation.tokenHash != InvitationToken.hash(entry.tokenValue)
            ) {
                authenticationDependency { queue.markFailed(entry.invitationId, now) }
                continue
            }
            val status =
                try {
                    email.send(entry.recipient, entry.tokenValue, entry.invitationId, entry.expiresAt)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    VerificationDeliveryStatus.UNCONFIRMED
                }
            when (status) {
                VerificationDeliveryStatus.ACCEPTED ->
                    authenticationDependency { queue.clearToken(entry.invitationId) }
                VerificationDeliveryStatus.DELIVERED ->
                    authenticationDependency {
                        queue.markDelivered(entry.invitationId, clock.instant())
                    }
                VerificationDeliveryStatus.FAILED, VerificationDeliveryStatus.UNAVAILABLE -> {
                    authenticationDependency { queue.markFailed(entry.invitationId, clock.instant()) }
                    authenticationDependency { repository.revoke(entry.invitationId, clock.instant()) }
                }
                else -> {
                    val delaySeconds = (60L shl entry.attempts.coerceIn(0, 4)).coerceAtMost(900L)
                    authenticationDependency {
                        queue.scheduleRetry(
                            entry.invitationId,
                            clock.instant().plusSeconds(delaySeconds),
                            clock.instant(),
                        )
                    }
                }
            }
            authenticationDependency { repository.updateDeliveryStatus(entry.invitationId, status.name, clock.instant()) }
        }
        return pending.size
    }

    private suspend fun requireInstitution(command: InvitationDispatchCommand) = requireInstitutionId(command.institutionId)

    private suspend fun requireInstitutionId(institutionId: String) {
        if (authenticationDependency { institutions.findEnabled(institutionId) } == null) {
            throw ApiException.Validation(
                "Institution does not accept new registrations",
                code = ApiErrorCode.INVITATION_INSTITUTION_UNAVAILABLE,
            )
        }
    }

    private fun retryDigest(
        actorId: String,
        invitationId: String,
        operationId: String,
    ): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(
                listOf("companero.invitation.retry.v2", actorId, invitationId, operationId)
                    .joinToString("\n")
                    .toByteArray(Charsets.UTF_8),
            ).joinToString("") { "%02x".format(it) }

    private suspend fun authorizedIssuer(actor: PlatformAccount): PlatformAccount {
        val current = authenticationDependency { accounts.findById(actor.id) } ?: throw ApiException.Unauthorized()
        if (!current.permitsSession ||
            current.authRevision != actor.authRevision ||
            current.roles != actor.roles ||
            current.accountStatus != actor.accountStatus ||
            current.institutionId != actor.institutionId
        ) {
            throw ApiException.Unauthorized()
        }
        if (current.accountStatus != AccountStatus.ACTIVE ||
            current.emailVerifiedAt == null ||
            current.emailVerifiedAt > clock.instant() ||
            UserRole.SUPER_ADMIN !in current.roles
        ) {
            throw ApiException.Forbidden("Verified super administrator is required to send invitations")
        }
        return current
    }

    private fun invitationId(
        actorId: String,
        operationId: String,
    ): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest
                .getInstance("SHA-256")
                .digest("companero.invitation.id.v1\n$actorId\n$operationId".toByteArray(Charsets.UTF_8)),
        )

    companion object {
        const val RESOURCE_ID = "platform-invitations"
    }
}
