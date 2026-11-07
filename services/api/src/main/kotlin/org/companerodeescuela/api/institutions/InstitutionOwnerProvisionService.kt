package org.companerodeescuela.api.institutions

import org.companerodeescuela.api.auth.PlatformAccount
import org.companerodeescuela.api.auth.PlatformAccountRepository
import org.companerodeescuela.api.auth.authenticationDependency
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.security.MfaService
import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.InstitutionSummary
import org.companerodeescuela.shared.contracts.StepUpAction
import org.companerodeescuela.shared.contracts.UserRole
import java.security.MessageDigest
import java.time.Clock
import java.util.Locale

/** Creation is independent of SIII; the owner decides which tenants accept registration. */
internal data class InstitutionProvisionCommand(
    val institutionId: String,
    val displayName: String,
    val operationId: String,
) {
    fun canonical(): InstitutionProvisionCommand = copy(institutionId = institutionId.trim(), displayName = displayName.trim())

    fun valid(): Boolean =
        institutionId.matches(Regex("[A-Za-z0-9_-]{2,64}")) &&
            displayName.length in 3..128 &&
            displayName == displayName.trim() &&
            displayName.none(Char::isISOControl) &&
            operationId.matches(Regex("[A-Za-z0-9_-]{8,128}"))

    fun digest(actorId: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(
                listOf(
                    "companero.platform.institution.create.v1",
                    actorId,
                    institutionId,
                    displayName,
                    operationId,
                    "registrationEnabled=true",
                ).joinToString("\n").toByteArray(Charsets.UTF_8),
            ).joinToString("") { "%02x".format(it) }
}

internal class InstitutionOwnerProvisionService(
    private val accounts: PlatformAccountRepository,
    private val institutions: InstitutionRepository,
    private val mfa: MfaService,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun prepare(
        actor: PlatformAccount,
        request: InstitutionProvisionCommand,
    ): String {
        val current = owner(actor)
        val command = request.canonical()
        if (!command.valid()) throw ApiException.Validation("Institution details are invalid")
        return command.digest(current.id)
    }

    suspend fun create(
        actor: PlatformAccount,
        request: InstitutionProvisionCommand,
        proof: String,
    ): InstitutionSummary {
        val current = owner(actor)
        val command = request.canonical()
        if (!command.valid()) throw ApiException.Validation("Institution details are invalid")
        authenticationDependency {
            mfa.requireStepUp(
                current,
                proof,
                StepUpAction.INSTITUTION_MANAGEMENT,
                RESOURCE_ID,
                command.operationId,
                command.digest(current.id),
            )
        }
        val institution = InstitutionSummary(command.institutionId, command.displayName)
        val created =
            authenticationDependency {
                institutions.createEnabled(institution, current.id, command.operationId, clock.instant())
            }
        if (!created) throw ApiException.Conflict("Institution already exists or could not be enabled")
        return institution
    }

    private suspend fun owner(actor: PlatformAccount): PlatformAccount {
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
            UserRole.SUPER_ADMIN !in current.roles ||
            current.email.trim().lowercase(Locale.ROOT) != OWNER_EMAIL ||
            current.emailVerifiedAt?.let { it <= clock.instant() } != true
        ) {
            throw ApiException.Forbidden("Verified platform owner is required to create institutions")
        }
        return current
    }

    companion object {
        const val RESOURCE_ID = "platform-institutions"
        const val OWNER_EMAIL = "erik55509@hotmail.com"
    }
}
