package org.companerodeescuela.api.auth

import com.mongodb.MongoWriteException
import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.exists
import com.mongodb.client.model.Filters.or
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Updates
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import java.time.Instant
import java.util.Date
import org.companerodeescuela.shared.contracts.RegistrationAccountType
import org.companerodeescuela.shared.contracts.VerificationDeliveryStatus
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bson.Document
import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.UserRole

class MongoPlatformAccountRepository(
    database: MongoDatabase,
) : PlatformAccountRepository {
    private val accounts: MongoCollection<Document> = database.getCollection(COLLECTION)
    private val indexMutex = Mutex()

    @Volatile
    private var indexesReady = false

    override suspend fun findByIdentifier(identifier: String): PlatformAccount? {
        ensureIndexes()
        val normalized = identifier.trim().lowercase()
        return accounts.find(
            or(
                eq("emailNormalized", normalized),
                eq("_id", identifier),
            ),
        ).firstOrNull()?.toAccount()
    }

    override suspend fun findById(id: String): PlatformAccount? {
        ensureIndexes()
        return accounts.find(eq("_id", id)).firstOrNull()?.toAccount()
    }

    override suspend fun create(account: PlatformAccount): Boolean {
        ensureIndexes()
        return try {
            accounts.insertOne(account.copy(email = account.email.trim().lowercase()).toDocument())
            true
        } catch (error: MongoWriteException) {
            if (error.error.code == DUPLICATE_KEY_CODE) false else throw error
        }
    }

    override suspend fun issueEmailVerification(accountId: String, expectedRevision: Long, hash: String, now: Instant, expiresAt: Instant): Boolean {
        val current = findById(accountId) ?: return false
        val updated = current.withEmailChallenge(expectedRevision, hash, now, expiresAt) ?: return false
        return accounts.findOneAndUpdate(
            and(eq("_id", accountId), eq("authRevision", expectedRevision), eq("active", true),
                eq("accountStatus", AccountStatus.PENDING_VERIFICATION.name), eq("emailVerifiedAt", null),
                eq("emailVerification.tokenHash", current.emailVerification?.tokenHash)),
            Updates.set("emailVerification", updated.emailVerification!!.toDocument()),
            FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER),
        ) != null
    }

    override suspend fun confirmEmailVerification(accountId: String, hash: String, now: Instant): PlatformAccount? {
        val current = findById(accountId) ?: return null
        val updated = current.withVerifiedEmail(hash, now) ?: return null
        return accounts.findOneAndUpdate(
            and(eq("_id", accountId), eq("authRevision", current.authRevision), eq("active", true),
                eq("accountStatus", AccountStatus.PENDING_VERIFICATION.name), eq("emailVerifiedAt", null),
                eq("emailVerification.tokenHash", hash), eq("emailVerification.authRevision", current.authRevision)),
            Updates.combine(
                Updates.set("accountStatus", updated.accountStatus.name), Updates.set("authRevision", updated.authRevision),
                Updates.set("roles", updated.roles.map(UserRole::name)), Updates.set("emailVerifiedAt", Date.from(now)),
                Updates.unset("emailVerification"), Updates.push("identityAudit", updated.identityAudit.last().toDocument()),
            ), FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER),
        )?.toAccount()
    }

    override suspend fun recordEmailDelivery(accountId: String, hash: String, status: VerificationDeliveryStatus): Boolean {
        val current = findById(accountId) ?: return false
        return accounts.updateOne(and(eq("_id", accountId), eq("authRevision", current.authRevision),
            eq("accountStatus", AccountStatus.PENDING_VERIFICATION.name), eq("emailVerification.tokenHash", hash),
            eq("emailVerification.authRevision", current.authRevision)),
            Updates.set("emailVerification.deliveryStatus", status.name)).matchedCount > 0
    }

    override suspend fun changeIdentity(accountId: String, expectedRevision: Long, change: AccountIdentityChange): PlatformAccount? {
        val current = findById(accountId) ?: return null
        if (current.isExactIdentityRetry(expectedRevision, change)) return current
        val updated = current.applyIdentityChange(expectedRevision, change) ?: return null
        val revisionFilter = if (expectedRevision == 0L)
            or(eq("authRevision", 0L), exists("authRevision", false)) else eq("authRevision", expectedRevision)
        val statusFilter = or(eq("accountStatus", current.accountStatus.name), exists("accountStatus", false))
        val result = accounts.findOneAndUpdate(
            and(eq("_id", accountId), revisionFilter, statusFilter, eq("active", current.active)),
            Updates.combine(
                Updates.set("accountStatus", updated.accountStatus.name),
                Updates.set("active", updated.active),
                Updates.set("authRevision", updated.authRevision),
                Updates.set("roles", updated.roles.map(UserRole::name)),
                Updates.set("institutionId", updated.institutionId),
                Updates.push("identityAudit", updated.identityAudit.last().toDocument()),
            ),
            FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER),
        )
        if (result != null) return result.toAccount()
        // A simultaneous exact retry may have committed first; a changed payload is never accepted.
        return findById(accountId)?.takeIf { it.isExactIdentityRetry(expectedRevision, change) }
    }

    private suspend fun ensureIndexes() {
        if (indexesReady) return
        indexMutex.withLock {
            if (indexesReady) return@withLock
            accounts.createIndex(
                Indexes.ascending("emailNormalized"),
                IndexOptions().unique(true).name("uq_platform_account_email"),
            )
            indexesReady = true
        }
    }

    private fun PlatformAccount.toDocument(): Document = Document()
        .append("_id", id)
        .append("displayName", displayName)
        .append("email", email)
        .append("emailNormalized", email.lowercase())
        .append("passwordHash", passwordHash)
        .append("roles", roles.map(UserRole::name))
        .append("active", active)
        .append("accountStatus", accountStatus.name)
        .append("authRevision", authRevision)
        .append("institutionId", institutionId)
        .append("identityAudit", identityAudit.map { it.toDocument() })
        .append("createdAt", Date.from(createdAt))
        .append("registrationIntent", registrationIntent?.toDocument())
        .append("emailVerification", emailVerification?.toDocument())
        .append("emailVerifiedAt", emailVerifiedAt?.let(Date::from))

    private fun Document.toAccount(): PlatformAccount = PlatformAccount(
        id = getString("_id"),
        displayName = getString("displayName"),
        email = getString("email"),
        passwordHash = getString("passwordHash"),
        roles = getList("roles", String::class.java)
            .orEmpty()
            .mapNotNull { encoded -> runCatching { UserRole.valueOf(encoded) }.getOrNull() }
            .toSet(),
        active = get("active") == true && validAuthorityRevision(),
        accountStatus = if (!containsKey("accountStatus")) {
            if (get("active") == true) AccountStatus.ACTIVE else AccountStatus.SUSPENDED
        } else runCatching { AccountStatus.valueOf(getString("accountStatus")) }.getOrDefault(AccountStatus.REVOKED),
        authRevision = if (validAuthorityRevision()) (get("authRevision") as? Number)?.toLong() ?: 0L else -1L,
        institutionId = getString("institutionId"),
        identityAudit = getList("identityAudit", Document::class.java).orEmpty().map { it.toIdentityAudit() },
        createdAt = getDate("createdAt").toInstant(),
        registrationIntent = get("registrationIntent", Document::class.java)?.toRegistrationIntent(),
        emailVerification = get("emailVerification", Document::class.java)?.toEmailChallenge(),
        emailVerifiedAt = getDate("emailVerifiedAt")?.toInstant(),
    )

    private fun RegistrationIntent.toDocument(): Document = Document("profile", profile.name)
        .append("requestedInstitutionId", requestedInstitutionId).append("identityReference", identityReference)
        .append("preferredGroup", preferredGroup)
    private fun Document.toRegistrationIntent(): RegistrationIntent? = runCatching {
        RegistrationIntent(RegistrationAccountType.valueOf(getString("profile")), getString("requestedInstitutionId"),
            getString("identityReference"), getString("preferredGroup"))
    }.getOrNull()
    private fun EmailVerificationChallenge.toDocument(): Document = Document("tokenHash", tokenHash)
        .append("issuedAt", Date.from(issuedAt)).append("expiresAt", Date.from(expiresAt))
        .append("authRevision", authRevision).append("recentIssuedAt", recentIssuedAt.map(Date::from))
        .append("deliveryStatus", deliveryStatus.name)
    private fun Document.toEmailChallenge(): EmailVerificationChallenge? = runCatching {
        val revision = get("authRevision")
        require(revision is Long || revision is Int)
        EmailVerificationChallenge(getString("tokenHash"), getDate("issuedAt").toInstant(), getDate("expiresAt").toInstant(),
            (revision as Number).toLong(), getList("recentIssuedAt", Date::class.java).map(Date::toInstant),
            VerificationDeliveryStatus.valueOf(getString("deliveryStatus")))
    }.getOrNull()

    private fun AccountIdentityAudit.toDocument(): Document = Document()
        .append("operationId", operationId).append("actorId", actorId)
        .append("fromRevision", fromRevision).append("toRevision", toRevision)
        .append("fromStatus", fromStatus.name).append("toStatus", toStatus.name)
        .append("rolesBefore", rolesBefore.map(UserRole::name)).append("rolesAfter", rolesAfter.map(UserRole::name))
        .append("institutionBefore", institutionBefore).append("institutionAfter", institutionAfter)
        .append("occurredAt", Date.from(occurredAt))

    private fun Document.toIdentityAudit(): AccountIdentityAudit = AccountIdentityAudit(
        operationId = getString("operationId"), actorId = getString("actorId"),
        fromRevision = (get("fromRevision") as Number).toLong(), toRevision = (get("toRevision") as Number).toLong(),
        fromStatus = AccountStatus.valueOf(getString("fromStatus")), toStatus = AccountStatus.valueOf(getString("toStatus")),
        rolesBefore = getList("rolesBefore", String::class.java).map(UserRole::valueOf).toSet(),
        rolesAfter = getList("rolesAfter", String::class.java).map(UserRole::valueOf).toSet(),
        institutionBefore = getString("institutionBefore"), institutionAfter = getString("institutionAfter"),
        occurredAt = getDate("occurredAt").toInstant(),
    )

    private fun Document.validAuthorityRevision(): Boolean = !containsKey("authRevision") ||
        ((get("authRevision") is Long || get("authRevision") is Int) && (get("authRevision") as Number).toLong() >= 0)

    private companion object {
        const val COLLECTION = "platform_accounts"
        const val DUPLICATE_KEY_CODE = 11000
    }
}
