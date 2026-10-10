package org.companerodeescuela.api.auth

import com.mongodb.MongoWriteException
import com.mongodb.client.model.Filters.or
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import java.util.Date
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
            accounts.insertOne(account.toDocument())
            true
        } catch (error: MongoWriteException) {
            if (error.error.code == DUPLICATE_KEY_CODE) false else throw error
        }
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
        .append("createdAt", Date.from(createdAt))

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
        createdAt = getDate("createdAt").toInstant(),
    )

    private fun Document.validAuthorityRevision(): Boolean = !containsKey("authRevision") ||
        ((get("authRevision") is Long || get("authRevision") is Int) && (get("authRevision") as Number).toLong() >= 0)

    private companion object {
        const val COLLECTION = "platform_accounts"
        const val DUPLICATE_KEY_CODE = 11000
    }
}
