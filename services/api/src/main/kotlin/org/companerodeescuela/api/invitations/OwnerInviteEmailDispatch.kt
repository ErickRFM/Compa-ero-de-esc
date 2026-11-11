package org.companerodeescuela.api.invitations

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.companerodeescuela.api.auth.OwnerInvitationRepository
import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.api.config.Environment
import org.companerodeescuela.shared.contracts.VerificationDeliveryStatus
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.util.Base64
import java.util.Locale

internal data class OwnerInviteDispatchConfig(
    val recipient: String,
    val code: String,
    val codeDigest: String,
    val expiresAt: Instant,
    val dispatchToken: String,
)

internal enum class OwnerInviteDispatchOutcome { ACCEPTED, ALREADY_ATTEMPTED, NOT_CONFIGURED, DELIVERY_FAILED }

internal class OwnerInviteEmailDispatchService(
    private val invitations: OwnerInvitationRepository,
    private val email: InvitationEmailGateway,
    private val clock: Clock = Clock.systemUTC(),
    private val random: SecureRandom = SecureRandom(),
) {
    suspend fun dispatch(
        suppliedToken: String?,
        config: OwnerInviteDispatchConfig?,
    ): OwnerInviteDispatchOutcome {
        val now = clock.instant()
        val validConfig = config?.takeIf { it.validAt(now) } ?: return OwnerInviteDispatchOutcome.NOT_CONFIGURED
        if (!sameSecret(suppliedToken, validConfig.dispatchToken)) return OwnerInviteDispatchOutcome.NOT_CONFIGURED

        val deliveryId = ByteArray(32).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        val claimed =
            invitations.claimDelivery(
                digest = validConfig.codeDigest,
                recipient = validConfig.recipient,
                expiresAt = validConfig.expiresAt,
                now = now,
                deliveryId = deliveryId,
            )
        if (!claimed) return OwnerInviteDispatchOutcome.ALREADY_ATTEMPTED

        return when (
            email.sendOwnerBootstrap(
                recipient = validConfig.recipient,
                code = validConfig.code,
                invitationId = deliveryId,
                expiresAt = validConfig.expiresAt,
            )
        ) {
            VerificationDeliveryStatus.ACCEPTED, VerificationDeliveryStatus.DELIVERED -> OwnerInviteDispatchOutcome.ACCEPTED
            else -> OwnerInviteDispatchOutcome.DELIVERY_FAILED
        }
    }
}

private fun OwnerInviteDispatchConfig.validAt(now: Instant): Boolean {
    val canonicalRecipient = recipient.trim().lowercase(Locale.ROOT)
    if (recipient != canonicalRecipient ||
        recipient.length !in 3..128 ||
        !Regex("^[^\\s@]+@[^\\s@]+$").matches(recipient) ||
        !Regex("[A-Za-z0-9_-]{43}").matches(code) ||
        !Regex("[a-f0-9]{64}").matches(codeDigest) ||
        !Regex("[A-Za-z0-9_-]{43}").matches(dispatchToken) ||
        expiresAt <= now ||
        expiresAt > now.plusSeconds(7 * 24 * 60 * 60L)
    ) {
        return false
    }
    val actualDigest = MessageDigest.getInstance("SHA-256").digest(code.toByteArray(Charsets.UTF_8))
    val configuredDigest = codeDigest.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    return MessageDigest.isEqual(actualDigest, configuredDigest)
}

private fun sameSecret(
    supplied: String?,
    expected: String,
): Boolean {
    if (supplied == null || supplied.length > 256) return false
    val digest = MessageDigest.getInstance("SHA-256")
    return MessageDigest.isEqual(
        digest.digest(supplied.toByteArray(Charsets.UTF_8)),
        digest.digest(expected.toByteArray(Charsets.UTF_8)),
    )
}

internal fun Route.ownerInviteEmailDispatchRoutes(
    settings: ApiSettings,
    service: OwnerInviteEmailDispatchService,
    environment: (String) -> String? = System::getenv,
    clock: Clock = Clock.systemUTC(),
) {
    route("/internal/owner-invitation") {
        post("/send") {
            if (settings.environment != Environment.PRODUCTION ||
                !settings.mongo.isConfigured ||
                !settings.hasVerificationEmail
            ) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }
            val expiry = environment("OWNER_INVITE_EXPIRES_AT")?.trim()?.toLongOrNull()
            val config =
                expiry?.let {
                    OwnerInviteDispatchConfig(
                        recipient = environment("OWNER_INVITE_EMAIL")?.trim()?.lowercase(Locale.ROOT).orEmpty(),
                        code = environment("OWNER_INVITE_CODE").orEmpty(),
                        codeDigest = environment("OWNER_INVITE_CODE_SHA256")?.trim().orEmpty(),
                        expiresAt = Instant.ofEpochSecond(it),
                        dispatchToken = environment("OWNER_INVITE_DISPATCH_TOKEN").orEmpty(),
                    )
                }
            val authorization = call.request.headers[HttpHeaders.Authorization]
            val suppliedToken = authorization?.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ")
            when (service.dispatch(suppliedToken, config)) {
                OwnerInviteDispatchOutcome.ACCEPTED -> call.respond(HttpStatusCode.Accepted)
                OwnerInviteDispatchOutcome.ALREADY_ATTEMPTED -> call.respond(HttpStatusCode.Conflict)
                OwnerInviteDispatchOutcome.NOT_CONFIGURED -> call.respond(HttpStatusCode.NotFound)
                OwnerInviteDispatchOutcome.DELIVERY_FAILED -> call.respond(HttpStatusCode.BadGateway)
            }
        }
    }
}
