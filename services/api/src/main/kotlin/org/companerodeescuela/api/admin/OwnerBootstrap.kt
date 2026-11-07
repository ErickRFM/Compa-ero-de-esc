package org.companerodeescuela.api.admin

import com.mongodb.MongoWriteException
import com.mongodb.client.model.Filters.eq
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bson.Document
import org.companerodeescuela.api.auth.AccountIdentityChange
import org.companerodeescuela.api.auth.PlatformAccountRepository
import org.companerodeescuela.api.security.MfaService
import org.companerodeescuela.shared.contracts.RegistrationAccountType
import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.StepUpAction
import org.companerodeescuela.shared.contracts.UserRole
import java.security.MessageDigest
import java.time.Clock
import java.util.Date
import java.util.Locale

/** QA-only command. The operator label is a declared value, not independently authenticated identity. */
data class OwnerBootstrapSpec(
    val accountId: String,
    val email: String,
    val expectedRevision: Long,
    val operationId: String,
    val operator: String,
    val reason: String,
    val expiresAt: Long,
    val proof: String,
    val scope: String = "qa",
) {
    init {
        require(scope in setOf("qa", "prod"))
    }

    fun digest(): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(
                listOf(
                    "companero.$scope-first-owner.v1",
                    accountId,
                    email,
                    expectedRevision.toString(),
                    operationId,
                    operator,
                    reason,
                    expiresAt.toString(),
                    "SUPER_ADMIN",
                ).joinToString("\n").toByteArray(Charsets.UTF_8),
            ).joinToString("") { "%02x".format(it) }

    override fun toString(): String = "OwnerBootstrapSpec([redacted])"
}

data class OwnerBootstrapClaim(
    val digest: String,
    val accountId: String,
    val operator: String,
    val reason: String,
    val authorizedAt: java.time.Instant,
)

interface OwnerBootstrapClaims {
    suspend fun find(): OwnerBootstrapClaim?

    suspend fun reserve(claim: OwnerBootstrapClaim): Boolean
}

class InMemoryOwnerBootstrapClaims : OwnerBootstrapClaims {
    private val mutex = Mutex()
    private var claim: OwnerBootstrapClaim? = null

    override suspend fun find(): OwnerBootstrapClaim? = mutex.withLock { claim }

    override suspend fun reserve(claim: OwnerBootstrapClaim): Boolean =
        mutex.withLock {
            val previous = this.claim
            if (previous == null) {
                this.claim = claim
                true
            } else {
                previous.digest == claim.digest
            }
        }
}

/** One durable global reservation per database; never releases revoked authority. */
class MongoOwnerBootstrapClaims(
    database: MongoDatabase,
) : OwnerBootstrapClaims {
    private val claims = database.getCollection<Document>("qa_initial_owner_claims")

    override suspend fun find(): OwnerBootstrapClaim? =
        claims.find(eq("_id", "global-owner")).firstOrNull()?.let {
            OwnerBootstrapClaim(
                it.getString("digest"),
                it.getString("accountId"),
                it.getString("operator"),
                it.getString("reason"),
                it.getDate("authorizedAt").toInstant(),
            )
        }

    override suspend fun reserve(claim: OwnerBootstrapClaim): Boolean {
        try {
            claims.insertOne(
                Document("_id", "global-owner")
                    .append("digest", claim.digest)
                    .append("accountId", claim.accountId)
                    .append("operator", claim.operator)
                    .append("reason", claim.reason)
                    .append("authorizedAt", Date.from(claim.authorizedAt)),
            )
            return true
        } catch (error: MongoWriteException) {
            if (error.error.code != 11000) throw error
        }
        return find()?.digest == claim.digest
    }
}

object OwnerBootstrap {
    /** Explicit production authorization, distinct from the QA flow and digest. */
    fun parseProduction(env: (String) -> String?): OwnerBootstrapSpec? {
        val enabled = env("PROD_OWNER_BOOTSTRAP_ENABLED") ?: "false"
        require(enabled in setOf("true", "false")) { "Invalid production owner flag" }
        if (enabled == "false") return null

        fun required(name: String) = requireNotNull(env(name)) { "Production owner configuration incomplete" }
        require(required("PROD_OWNER_ISOLATION_CERTIFIED") == "true") {
            "Production database isolation must be certified before bootstrap"
        }
        val revision = requireNotNull(required("PROD_OWNER_BOOTSTRAP_EXPECTED_REVISION").toLongOrNull())
        val expiry = requireNotNull(required("PROD_OWNER_BOOTSTRAP_EXPIRES_AT").toLongOrNull())
        val proof = required("PROD_OWNER_BOOTSTRAP_PROOF")
        require(proof.matches(Regex("[A-Za-z0-9_-]{43}"))) { "Invalid production MFA proof" }
        return OwnerBootstrapSpec(
            required("PROD_OWNER_BOOTSTRAP_ACCOUNT_ID"),
            required("PROD_OWNER_BOOTSTRAP_EMAIL"),
            revision,
            required("PROD_OWNER_BOOTSTRAP_OPERATION_ID"),
            required("PROD_OWNER_BOOTSTRAP_OPERATOR"),
            required("PROD_OWNER_BOOTSTRAP_REASON"),
            expiry,
            proof,
            scope = "prod",
        )
    }

    /** The flag is an operator-controlled startup opt-in; never callable through a public route. */
    fun parse(env: (String) -> String?): OwnerBootstrapSpec? {
        val enabled = env("QA_OWNER_BOOTSTRAP_ENABLED") ?: "false"
        require(enabled in setOf("true", "false")) { "QA owner bootstrap flag is invalid" }
        if (enabled == "false") return null

        fun required(name: String) = requireNotNull(env(name)) { "QA owner bootstrap configuration is incomplete" }
        val revision = requireNotNull(required("QA_OWNER_BOOTSTRAP_EXPECTED_REVISION").toLongOrNull())
        val expiry = requireNotNull(required("QA_OWNER_BOOTSTRAP_EXPIRES_AT").toLongOrNull())
        val proof = required("QA_OWNER_BOOTSTRAP_PROOF")
        require(proof.matches(Regex("[A-Za-z0-9_-]{43}"))) { "QA owner MFA proof is invalid" }
        return OwnerBootstrapSpec(
            required("QA_OWNER_BOOTSTRAP_ACCOUNT_ID"),
            required("QA_OWNER_BOOTSTRAP_EMAIL"),
            revision,
            required("QA_OWNER_BOOTSTRAP_OPERATION_ID"),
            required("QA_OWNER_BOOTSTRAP_OPERATOR"),
            required("QA_OWNER_BOOTSTRAP_REASON"),
            expiry,
            proof,
        )
    }

    suspend fun provision(
        spec: OwnerBootstrapSpec,
        environment: String,
        databaseName: String,
        accounts: PlatformAccountRepository,
        mfa: MfaService,
        claims: OwnerBootstrapClaims,
        clock: Clock = Clock.systemUTC(),
    ) {
        val permitted =
            when (spec.scope) {
                "qa" -> environment in setOf("local", "staging") && databaseName.matches(Regex("[A-Za-z0-9_]+_qa"))
                "prod" -> environment == "production" && databaseName.matches(Regex("[A-Za-z0-9_]+_prod"))
                else -> false
            }
        require(permitted) { "Owner bootstrap requires a matching isolated database and runtime" }
        require(spec.accountId.matches(Regex("[A-Za-z0-9_-]{1,128}")) && spec.operationId.matches(Regex("[A-Za-z0-9_-]{1,128}")))
        require(spec.operator.matches(Regex("[A-Za-z0-9_.@-]{1,100}")))
        require(spec.reason.length in 8..240 && spec.reason == spec.reason.trim() && spec.reason.none(Char::isISOControl))
        require(
            spec.email == spec.email.trim().lowercase(Locale.ROOT) &&
                spec.email.length in 3..128 &&
                '@' in spec.email &&
                spec.email.none(Char::isISOControl),
        )
        val now = clock.instant()
        require(
            spec.expectedRevision >= 0 &&
                spec.expectedRevision < 9_007_199_254_740_991L &&
                spec.expiresAt > 0,
        )
        val account = requireNotNull(accounts.findById(spec.accountId)) { "Verified owner candidate is required" }
        val digest = spec.digest()
        val declaredOperator = "${spec.scope}-declared:${spec.operator}"
        val existing = claims.find()
        val audit = account.identityAudit.lastOrNull()
        if (existing?.digest == digest &&
            existing.accountId == spec.accountId &&
            audit?.scopeDigest == digest &&
            audit.operationId == spec.operationId &&
            audit.actorId == declaredOperator &&
            audit.fromRevision == spec.expectedRevision &&
            audit.rolesAfter == account.roles &&
            audit.institutionAfter == account.institutionId &&
            account.email.trim().lowercase(Locale.ROOT) == spec.email &&
            account.authRevision == spec.expectedRevision + 1 &&
            UserRole.SUPER_ADMIN in account.roles &&
            account.accountStatus == AccountStatus.ACTIVE
        ) {
            return
        }
        require(spec.expiresAt > now.epochSecond && spec.expiresAt <= now.epochSecond + 900) {
            "QA owner authorization expired or exceeds fifteen minutes"
        }
        require(
            account.permitsSession &&
                account.accountStatus == AccountStatus.ACTIVE &&
                account.emailVerifiedAt?.let { it <= now } == true &&
                account.authRevision == spec.expectedRevision &&
                account.email.trim().lowercase(Locale.ROOT) == spec.email &&
                account.roles.isNotEmpty() &&
                account.roles.all { it in setOf(UserRole.STUDENT, UserRole.WORKSHOP_PARTICIPANT) },
        ) {
            "Owner candidate must be current, verified and unprivileged"
        }
        require(accounts.listAccounts().none { UserRole.SUPER_ADMIN in it.roles }) { "Existing owner requires normal administration" }
        mfa.requireStepUp(account, spec.proof, StepUpAction.PRIVILEGED_INVITE_ACCEPT, "global-owner", spec.operationId, digest)
        require(claims.reserve(OwnerBootstrapClaim(digest, account.id, spec.operator, spec.reason, now))) { "Owner reservation conflicts" }
        requireNotNull(
            accounts.changeIdentity(
                account.id,
                spec.expectedRevision,
                AccountIdentityChange(
                    spec.operationId,
                    declaredOperator,
                    AccountStatus.ACTIVE,
                    if (spec.scope == "prod" && account.registrationIntent?.profile == RegistrationAccountType.OWNER_INVITED) {
                        setOf(UserRole.SUPER_ADMIN)
                    } else {
                        account.roles + UserRole.SUPER_ADMIN
                    },
                    account.institutionId,
                    now,
                    scopeDigest = digest,
                ),
            ),
        ) { "Owner candidate changed; authority was not granted" }
    }
}
