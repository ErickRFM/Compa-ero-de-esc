package org.companerodeescuela.api.auth

import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.UserSummary
import org.companerodeescuela.shared.contracts.UserRole

data class PlatformAccount(
    val id: String,
    val displayName: String,
    val email: String,
    val passwordHash: String,
    val roles: Set<UserRole>,
    val active: Boolean = true,
    val createdAt: Instant = Instant.now(),
    val accountStatus: AccountStatus = if (active) AccountStatus.ACTIVE else AccountStatus.SUSPENDED,
    val authRevision: Long = 0,
    val institutionId: String? = null,
    val identityAudit: List<AccountIdentityAudit> = emptyList(),
) {
    val permitsSession: Boolean get() = active && accountStatus.permitsSession && authRevision >= 0

    fun toUserSummary(): UserSummary = UserSummary(
        id, displayName, email,
        roles = if (accountStatus == AccountStatus.ACTIVE) roles else emptySet(),
        active = permitsSession,
        accountStatus = accountStatus,
        authRevision = authRevision,
        institutionId = institutionId,
    )
}

interface PlatformAccountRepository {
    suspend fun findByIdentifier(identifier: String): PlatformAccount?
    suspend fun findById(id: String): PlatformAccount?
    suspend fun create(account: PlatformAccount): Boolean

    /** Internal CAS persistence; authorization and step-up are mandatory in the calling service. */
    suspend fun changeIdentity(accountId: String, expectedRevision: Long, change: AccountIdentityChange): PlatformAccount? = null
}

class InMemoryPlatformAccountRepository : PlatformAccountRepository {
    private val mutex = Mutex()
    private val accounts = mutableMapOf<String, PlatformAccount>()

    override suspend fun findByIdentifier(identifier: String): PlatformAccount? = mutex.withLock {
        val normalized = identifier.trim().lowercase()
        accounts.values.firstOrNull { it.email == normalized || it.id == identifier }
    }

    override suspend fun findById(id: String): PlatformAccount? = mutex.withLock { accounts[id] }

    override suspend fun create(account: PlatformAccount): Boolean = mutex.withLock {
        val normalized = account.email.trim().lowercase()
        if (account.id in accounts || accounts.values.any { it.email == normalized }) return@withLock false
        accounts[account.id] = account.copy(email = normalized, roles = account.roles.toSet())
        true
    }

    override suspend fun changeIdentity(accountId: String, expectedRevision: Long, change: AccountIdentityChange): PlatformAccount? =
        mutex.withLock {
            val current = accounts[accountId] ?: return@withLock null
            if (current.isExactIdentityRetry(expectedRevision, change)) return@withLock current
            val updated = current.applyIdentityChange(expectedRevision, change) ?: return@withLock null
            accounts[accountId] = updated
            updated
        }
}
