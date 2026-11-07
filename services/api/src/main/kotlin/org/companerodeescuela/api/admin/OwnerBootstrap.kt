package org.companerodeescuela.api.admin

import com.mongodb.MongoWriteException
import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.lte
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bson.Document
import org.companerodeescuela.api.auth.AccountIdentityChange
import org.companerodeescuela.api.auth.PlatformAccountRepository
import org.companerodeescuela.api.security.MfaService
import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.RegistrationAccountType
import org.companerodeescuela.shared.contracts.StepUpAction
import org.companerodeescuela.shared.contracts.UserRole
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.time.Clock
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.Locale

/** Startup-only command. Production requires a signed operator approval plus the candidate's MFA step-up. */
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
    val authorizationPublicKey: String? = null,
    val authorizationSignature: String? = null,
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

enum class OwnerBootstrapClaimState {
    RESERVED,
    COMMITTED,
}

data class OwnerBootstrapClaim(
    val digest: String,
    val accountId: String,
    val operator: String,
    val reason: String,
    val authorizedAt: Instant,
    val expiresAt: Instant,
    val state: OwnerBootstrapClaimState = OwnerBootstrapClaimState.RESERVED,
)

interface OwnerBootstrapClaims {
    suspend fun find(): OwnerBootstrapClaim?

    suspend fun reserve(claim: OwnerBootstrapClaim): Boolean

    suspend fun commit(
        digest: String,
        accountId: String,
        committedAt: Instant,
    ): Boolean
}

class InMemoryOwnerBootstrapClaims : OwnerBootstrapClaims {
    private val mutex = Mutex()
    private var claim: OwnerBootstrapClaim? = null

    override suspend fun find(): OwnerBootstrapClaim? = mutex.withLock { claim }

    override suspend fun reserve(claim: OwnerBootstrapClaim): Boolean =
        mutex.withLock {
            val previous = this.claim
            when {
                previous == null -> {
                    this.claim = claim
                    true
                }
                previous.digest == claim.digest -> previous.state == OwnerBootstrapClaimState.RESERVED
                previous.state == OwnerBootstrapClaimState.RESERVED &&
                    !previous.expiresAt.isAfter(claim.authorizedAt) -> {
                    this.claim = claim
                    true
                }
                else -> false
            }
        }

    override suspend fun commit(
        digest: String,
        accountId: String,
        committedAt: Instant,
    ): Boolean =
        mutex.withLock {
            val current = claim ?: return@withLock false
            if (current.digest != digest || current.accountId != accountId) return@withLock false
            if (current.state == OwnerBootstrapClaimState.COMMITTED) return@withLock true
            claim = current.copy(state = OwnerBootstrapClaimState.COMMITTED)
            true
        }
}

/**
 * One durable global reservation per database.
 *
 * A RESERVED claim is a short-lived lease tied to the signed command expiry. It can be
 * replaced only after expiry, using a new independently authorized command. COMMITTED
 * claims are permanent tombstones and are never reassigned. Legacy claims without an
 * explicit state are treated as COMMITTED (fail closed).
 */
class MongoOwnerBootstrapClaims(
    database: MongoDatabase,
) : OwnerBootstrapClaims {
    // The collection name is retained for backwards compatibility with existing QA claims.
    private val claims = database.getCollection<Document>("qa_initial_owner_claims")

    override suspend fun find(): OwnerBootstrapClaim? =
        claims.find(eq("_id", "global-owner")).firstOrNull()?.let(::toClaim)

    override suspend fun reserve(claim: OwnerBootstrapClaim): Boolean {
        try {
            claims.insertOne(
                Document("_id", "global-owner")
                    .append("digest", claim.digest)
                    .append("accountId", claim.accountId)
                    .append("operator", claim.operator)
                    .append("reason", claim.reason)
                    .append("authorizedAt", Date.from(claim.authorizedAt))
                    .append("expiresAt", Date.from(claim.expiresAt))
                    .append("state", OwnerBootstrapClaimState.RESERVED.name),
            )
            return true
        } catch (error: MongoWriteException) {
            if (error.error.code != 11000) throw error
        }

        val previous = find() ?: return false
        if (previous.digest == claim.digest) {
            return previous.state == OwnerBootstrapClaimState.RESERVED
        }
        if (previous.state != OwnerBootstrapClaimState.RESERVED ||
            previous.expiresAt.isAfter(claim.authorizedAt)
        ) {
            return false
        }

        // Compare-and-set the expired lease. Concurrent recovery commands cannot both win.
        return claims.findOneAndUpdate(
            and(
                eq("_id", "global-owner"),
                eq("digest", previous.digest),
                eq("state", OwnerBootstrapClaimState.RESERVED.name),
                lte("expiresAt", Date.from(claim.authorizedAt)),
            ),
            Updates.combine(
                Updates.set("digest", claim.digest),
                Updates.set("accountId", claim.accountId),
                Updates.set("operator", claim.operator),
                Updates.set("reason", claim.reason),
                Updates.set("authorizedAt", Date.from(claim.authorizedAt)),
                Updates.set("expiresAt", Date.from(claim.expiresAt)),
                Updates.set("state", OwnerBootstrapClaimState.RESERVED.name),
                Updates.set("recoveredFromDigest", previous.digest),
                Updates.set("recoveredAt", Date.from(claim.authorizedAt)),
            ),
            FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER),
        ) != null
    }

    override suspend fun commit(
        digest: String,
        accountId: String,
        committedAt: Instant,
    ): Boolean {
        val committed =
            claims.findOneAndUpdate(
                and(
                    eq("_id", "global-owner"),
                    eq("digest", digest),
                    eq("accountId", accountId),
                    eq("state", OwnerBootstrapClaimState.RESERVED.name),
                ),
                Updates.combine(
                    Updates.set("state", OwnerBootstrapClaimState.COMMITTED.name),
                    Updates.set("committedAt", Date.from(committedAt)),
                ),
                FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER),
            )
        if (committed != null) return true

        val current = find()
        return current?.digest == digest &&
            current.accountId == accountId &&
            current.state == OwnerBootstrapClaimState.COMMITTED
    }

    private fun toClaim(document: Document): OwnerBootstrapClaim {
        val state =
            runCatching {
                OwnerBootstrapClaimState.valueOf(document.getString("state") ?: OwnerBootstrapClaimState.COMMITTED.name)
            }.getOrDefault(OwnerBootstrapClaimState.COMMITTED)
        return OwnerBootstrapClaim(
            document.getString("digest"),
            document.getString("accountId"),
            document.getString("operator"),
            document.getString("reason"),
            document.getDate("authorizedAt").toInstant(),
            document.getDate("expiresAt")?.toInstant() ?: Instant.MAX,
            state,
        )
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
        val spec =
            OwnerBootstrapSpec(
                required("PROD_OWNER_BOOTSTRAP_ACCOUNT_ID"),
                required("PROD_OWNER_BOOTSTRAP_EMAIL"),
                revision,
                required("PROD_OWNER_BOOTSTRAP_OPERATION_ID"),
                required("PROD_OWNER_BOOTSTRAP_OPERATOR"),
                required("PROD_OWNER_BOOTSTRAP_REASON"),
                expiry,
                proof,
                scope = "prod",
                authorizationPublicKey = required("PROD_OWNER_BOOTSTRAP_TRUSTED_OPERATOR_PUBLIC_KEY"),
                authorizationSignature = required("PROD_OWNER_BOOTSTRAP_AUTHORIZATION"),
            )
        require(spec.hasValidOperatorAuthorization()) {
            "Production owner authorization signature is invalid"
        }
        return spec
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
        if (spec.scope == "prod") {
            require(spec.hasValidOperatorAuthorization()) { "Production owner authorization signature is invalid" }
        }
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
            require(claims.commit(digest, account.id, now)) { "Owner bootstrap claim could not be finalized" }
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
        if (spec.scope == "prod") {
            require(
                account.registrationIntent?.profile == RegistrationAccountType.OWNER_INVITED &&
                    account.roles == setOf(UserRole.WORKSHOP_PARTICIPANT),
            ) {
                "Production first owner must be a verified invitation-only owner candidate"
            }
        }
        require(accounts.listAccounts().none { UserRole.SUPER_ADMIN in it.roles }) { "Existing owner requires normal administration" }
        mfa.requireStepUp(account, spec.proof, StepUpAction.PRIVILEGED_INVITE_ACCEPT, "global-owner", spec.operationId, digest)
        require(
            claims.reserve(
                OwnerBootstrapClaim(
                    digest,
                    account.id,
                    spec.operator,
                    spec.reason,
                    now,
                    Instant.ofEpochSecond(spec.expiresAt),
                ),
            ),
        ) { "Owner reservation conflicts" }
        requireNotNull(
            accounts.changeIdentity(
                account.id,
                spec.expectedRevision,
                AccountIdentityChange(
                    spec.operationId,
                    declaredOperator,
                    AccountStatus.ACTIVE,
                    if (spec.scope == "prod") setOf(UserRole.SUPER_ADMIN) else account.roles + UserRole.SUPER_ADMIN,
                    account.institutionId,
                    now,
                    scopeDigest = digest,
                ),
            ),
        ) { "Owner candidate changed; authority was not granted" }
        require(claims.commit(digest, account.id, now)) {
            "Owner identity changed but its reservation is not committed; rerun the same authorized command"
        }
    }
}

/** The Ed25519 signature covers the canonical command digest, including operator, account, revision, reason and expiry. */
private fun OwnerBootstrapSpec.hasValidOperatorAuthorization(): Boolean {
    val publicKeyValue = authorizationPublicKey ?: return false
    val signatureValue = authorizationSignature ?: return false
    if (!publicKeyValue.matches(Regex("[A-Za-z0-9_-]{40,200}")) ||
        !signatureValue.matches(Regex("[A-Za-z0-9_-]{80,120}"))
    ) {
        return false
    }
    return runCatching {
        val publicKey =
            KeyFactory.getInstance("Ed25519").generatePublic(
                X509EncodedKeySpec(Base64.getUrlDecoder().decode(publicKeyValue)),
            )
        val verifier = Signature.getInstance("Ed25519")
        verifier.initVerify(publicKey)
        verifier.update(digest().toByteArray(Charsets.UTF_8))
        verifier.verify(Base64.getUrlDecoder().decode(signatureValue))
    }.getOrDefault(false)
}
