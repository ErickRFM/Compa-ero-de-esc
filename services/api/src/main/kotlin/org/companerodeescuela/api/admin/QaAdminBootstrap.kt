package org.companerodeescuela.api.admin

import com.mongodb.MongoWriteException
import com.mongodb.client.model.Filters.eq
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.firstOrNull
import org.bson.Document
import org.companerodeescuela.api.auth.AccountIdentityChange
import org.companerodeescuela.api.auth.PlatformAccountRepository
import org.companerodeescuela.api.institutions.InstitutionRepository
import org.companerodeescuela.api.security.MfaService
import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.AdminCapability
import org.companerodeescuela.shared.contracts.StepUpAction
import org.companerodeescuela.shared.contracts.UserRole
import java.security.MessageDigest
import java.time.Clock

/** Operator opt-in is separate from the candidate's verified email and scoped MFA consent.
 * Never used in the shared development database or in production. No password is generated or reset.
 */
class QaAdminBootstrapSpec(
    val institutionId: String,
    val accountId: String,
    val expectedRevision: Long,
    val operationId: String,
    val expiresAt: Long,
    val proof: String,
) {
    fun digest(): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(
                listOf(
                    "companero.qa-first-admin.v1",
                    institutionId,
                    accountId,
                    expectedRevision.toString(),
                    operationId,
                    expiresAt.toString(),
                    "ADMIN",
                    "APPROVE_STAFF,MANAGE_ACADEMICS,MANAGE_USERS",
                    "registrationEnabled=true",
                ).joinToString("\n").toByteArray(Charsets.UTF_8),
            ).joinToString("") { "%02x".format(it) }

    override fun toString() = "QaAdminBootstrapSpec([redacted])"
}

interface QaAdminBootstrapClaims {
    suspend fun activateRegistration(
        institutionId: String,
        digest: String,
    ): Boolean

    /** Durable, permanent institution reservation; identical recovery only. No TTL resurrection. */
    suspend fun claim(
        institutionId: String,
        digest: String,
    ): Boolean
}

class MongoQaAdminBootstrapClaims(
    database: MongoDatabase,
) : QaAdminBootstrapClaims {
    private val claims = database.getCollection<Document>("qa_initial_admin_claims")
    private val institutions = database.getCollection<Document>("platform_institutions")

    override suspend fun activateRegistration(
        institutionId: String,
        digest: String,
    ): Boolean {
        if (claims.find(eq("_id", institutionId)).firstOrNull()?.getString("digest") != digest) return false
        val changed =
            institutions
                .updateOne(
                    com.mongodb.client.model.Filters
                        .and(eq("_id", institutionId), eq("displayName", "QA-UNIV"), eq("qaBootstrapDigest", null)),
                    com.mongodb.client.model.Updates.combine(
                        com.mongodb.client.model.Updates
                            .set("registrationEnabled", true),
                        com.mongodb.client.model.Updates
                            .set("qaBootstrapDigest", digest),
                    ),
                ).matchedCount == 1L
        if (changed) return true
        val current = institutions.find(eq("_id", institutionId)).firstOrNull()
        return current?.getString("qaBootstrapDigest") == digest && current.getBoolean("registrationEnabled") == true
    }

    override suspend fun claim(
        institutionId: String,
        digest: String,
    ): Boolean {
        try {
            claims.insertOne(Document("_id", institutionId).append("digest", digest))
            return true
        } catch (error: MongoWriteException) {
            if (error.error.code != 11000) throw error
        }
        return claims.find(eq("_id", institutionId)).firstOrNull()?.getString("digest") == digest
    }
}

object QaAdminBootstrap {
    fun parse(env: (String) -> String?): QaAdminBootstrapSpec? {
        val enabled = env("QA_ADMIN_BOOTSTRAP_ENABLED") ?: "false"
        require(enabled in setOf("false", "true")) { "QA bootstrap flag is invalid" }
        if (enabled == "false") return null

        fun id(name: String) =
            requireNotNull(env(name)).also {
                require(it.matches(Regex("[A-Za-z0-9_-]{1,128}"))) { "QA bootstrap identifier is invalid" }
            }
        val revision = requireNotNull(env("QA_ADMIN_BOOTSTRAP_EXPECTED_REVISION")?.toLongOrNull())
        val expiry = requireNotNull(env("QA_ADMIN_BOOTSTRAP_EXPIRES_AT")?.toLongOrNull())
        val proof = requireNotNull(env("QA_ADMIN_BOOTSTRAP_PROOF"))
        require(revision in 0 until 9_007_199_254_740_991L && expiry > 0 && proof.matches(Regex("[A-Za-z0-9_-]{43}"))) {
            "QA bootstrap evidence is invalid"
        }
        return QaAdminBootstrapSpec(
            id("QA_ADMIN_BOOTSTRAP_INSTITUTION_ID"),
            id("QA_ADMIN_BOOTSTRAP_ACCOUNT_ID"),
            revision,
            id("QA_ADMIN_BOOTSTRAP_OPERATION_ID"),
            expiry,
            proof,
        )
    }

    suspend fun provision(
        spec: QaAdminBootstrapSpec,
        environment: String,
        databaseName: String,
        accounts: PlatformAccountRepository,
        institutions: InstitutionRepository,
        mfa: MfaService,
        claims: QaAdminBootstrapClaims,
        clock: Clock = Clock.systemUTC(),
    ) {
        require(environment in setOf("local", "staging") && databaseName.matches(Regex("[A-Za-z0-9_]+_qa"))) {
            "QA bootstrap requires an isolated QA database and nonproduction runtime"
        }
        val now = clock.instant()
        require(spec.expiresAt > now.epochSecond && spec.expiresAt <= now.epochSecond + 900) {
            "QA bootstrap authorization expired or exceeds fifteen minutes"
        }
        require(institutions.findConfigured(spec.institutionId)?.displayName == "QA-UNIV") { "QA institution metadata is required" }
        val account = requireNotNull(accounts.findById(spec.accountId)) { "Verified QA candidate is required" }
        val digest = spec.digest()
        val capabilities = setOf(AdminCapability.APPROVE_STAFF, AdminCapability.MANAGE_ACADEMICS, AdminCapability.MANAGE_USERS)
        // An exact startup retry may observe the already committed CAS, but never regrant revoked authority.
        val committed = account.identityAudit.lastOrNull()
        if (committed?.operationId == spec.operationId &&
            committed.scopeDigest == digest &&
            committed.fromRevision == spec.expectedRevision &&
            account.authRevision == spec.expectedRevision + 1 &&
            account.roles == setOf(UserRole.ADMIN) &&
            account.institutionId == spec.institutionId &&
            account.accountStatus == AccountStatus.ACTIVE &&
            account.adminCapabilities == capabilities
        ) {
            require(claims.claim(spec.institutionId, digest)) { "QA bootstrap reservation conflicts" }
            require(claims.activateRegistration(spec.institutionId, digest)) { "QA registration activation failed" }
            return
        }
        require(
            account.permitsSession &&
                account.accountStatus == AccountStatus.ACTIVE &&
                account.emailVerifiedAt?.let { it <= now } == true &&
                account.authRevision == spec.expectedRevision &&
                account.roles.isNotEmpty() &&
                account.roles.all { it in setOf(UserRole.STUDENT, UserRole.WORKSHOP_PARTICIPANT) } &&
                (account.institutionId == null || account.institutionId == spec.institutionId),
        ) { "QA candidate is not a current verified unprivileged identity" }
        require(
            accounts.listAccounts().none { candidate ->
                UserRole.SUPER_ADMIN in candidate.roles ||
                    (UserRole.ADMIN in candidate.roles && candidate.institutionId == spec.institutionId)
            },
        ) { "QA institution already has administrative authority; use normal administration" }
        mfa.requireStepUp(account, spec.proof, StepUpAction.PRIVILEGED_INVITE_ACCEPT, spec.institutionId, spec.operationId, digest)
        require(claims.claim(spec.institutionId, digest)) { "QA bootstrap has already been reserved for another command" }
        requireNotNull(
            accounts.changeIdentity(
                account.id,
                spec.expectedRevision,
                AccountIdentityChange(
                    operationId = spec.operationId,
                    actorId = account.id,
                    accountStatus = AccountStatus.ACTIVE,
                    roles = setOf(UserRole.ADMIN),
                    institutionId = spec.institutionId,
                    occurredAt = now,
                    scopeDigest = digest,
                    capabilities = capabilities,
                ),
            ),
        ) { "QA candidate changed; bootstrap did not grant authority" }
        require(claims.activateRegistration(spec.institutionId, digest)) { "QA registration activation failed" }
    }
}
