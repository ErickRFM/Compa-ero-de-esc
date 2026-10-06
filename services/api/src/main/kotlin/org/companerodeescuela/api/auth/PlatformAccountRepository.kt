package org.companerodeescuela.api.auth

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import org.companerodeescuela.shared.contracts.UserRole

data class PlatformAccount(
    val id: String,
    val displayName: String,
    val email: String,
    val passwordHash: String,
    val roles: Set<UserRole>,
    val active: Boolean = true,
    val createdAt: Instant = Instant.now(),
)

interface PlatformAccountRepository {
    suspend fun findByIdentifier(identifier: String): PlatformAccount?
    suspend fun findById(id: String): PlatformAccount?
    suspend fun create(account: PlatformAccount): Boolean
}

class InMemoryPlatformAccountRepository : PlatformAccountRepository {
    private val accounts = ConcurrentHashMap<String, PlatformAccount>()

    override suspend fun findByIdentifier(identifier: String): PlatformAccount? {
        val normalized = identifier.trim().lowercase()
        return accounts.values.firstOrNull {
            it.email.lowercase() == normalized || it.id.lowercase() == normalized
        }
    }

    override suspend fun findById(id: String): PlatformAccount? = accounts[id]

    override suspend fun create(account: PlatformAccount): Boolean {
        if (accounts.values.any { it.email.equals(account.email, ignoreCase = true) }) return false
        return accounts.putIfAbsent(account.id, account) == null
    }
}
