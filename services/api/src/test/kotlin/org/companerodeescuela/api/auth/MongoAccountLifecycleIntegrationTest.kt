package org.companerodeescuela.api.auth

import com.auth0.jwt.JWT
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.Timeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertFailsWith
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.integrations.mock.MockIdentityProvider
import org.companerodeescuela.api.test.TestFixtures
import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.LoginRequest
import org.companerodeescuela.shared.contracts.RefreshSessionRequest
import org.companerodeescuela.shared.contracts.UserRole

/** Actual MongoDB integration, skipped locally unless the isolated CI database is configured. */
@EnabledIfEnvironmentVariable(named = "V12_TEST_MONGODB_URI", matches = ".+")
@Timeout(120)
class MongoAccountLifecycleIntegrationTest {
    @Test
    fun `real Mongo unique email index rejects concurrent normalized duplicates`() = withDatabase { db ->
        val accounts = MongoPlatformAccountRepository(db)
        val results = coroutineScope { (1..16).map { n -> async {
            accounts.create(PlatformAccount("account-$n", "QA", if (n % 2 == 0) " QA@EXAMPLE.TEST " else "qa@example.test",
                "test-only-hash", setOf(UserRole.STUDENT)))
        } }.awaitAll() }
        assertEquals(1, results.count { it })
        assertEquals("qa@example.test", assertNotNull(accounts.findByIdentifier(" QA@EXAMPLE.TEST ")).email)
    }

    @Test
    fun `real Mongo CAS keeps one grant and one audit on concurrent approval and retries`() = withDatabase { db ->
        val accounts = MongoPlatformAccountRepository(db)
        accounts.create(PlatformAccount("account-1", "QA", "qa@example.test", "test-only-hash", setOf(UserRole.TEACHER_PENDING)))
        val command = AccountIdentityChange("approve", "admin-1", AccountStatus.ACTIVE, setOf(UserRole.TEACHER), "institution-a", Instant.now())
        val results = coroutineScope { (1..16).map { n -> async {
            accounts.changeIdentity("account-1", 0, command.copy(operationId = "approve-$n"))
        } }.awaitAll() }
        assertEquals(1, results.count { it != null })
        val updated = assertNotNull(accounts.findById("account-1"))
        assertEquals(1L, updated.authRevision)
        assertEquals(1, updated.identityAudit.size)
        val retry = accounts.changeIdentity("account-1", 0, command.copy(operationId = updated.identityAudit.single().operationId))
        assertEquals(updated, retry)
    }

    @Test
    fun `real Mongo persisted sessions never revive after suspension and restoration`() = withDatabase { db ->
        val accounts = MongoPlatformAccountRepository(db)
        val sessions = MongoRefreshSessionRepository(db)
        accounts.create(PlatformAccount("account-1", "QA", "qa@example.test", PasswordHasher().hash("test-password"), setOf(UserRole.STUDENT)))
        val settings = TestFixtures.settings().copy(jwtSecret = "0123456789abcdef0123456789abcdef".toCharArray())
        val tokens = AuthTokenService(settings)
        val provider = MockIdentityProvider()
        val service = AuthService(provider, tokens, sessions, accounts)
        val initial = service.login(LoginRequest("qa@example.test", "test-password"))
        val command = AccountIdentityChange("suspend", "admin-1", AccountStatus.SUSPENDED, setOf(UserRole.STUDENT), "institution-a", Instant.now())
        assertNotNull(accounts.changeIdentity("account-1", 0, command))
        assertNotNull(accounts.changeIdentity("account-1", 1, command.copy(operationId = "restore", accountStatus = AccountStatus.ACTIVE)))
        val authority = PlatformSessionAuthority(accounts, provider)
        assertFalse(authority.permits(JWT.decode(initial.accessToken), assertNotNull(sessions.find(initial.sessionId)), tokens))
        assertFailsWith<ApiException.Unauthorized> { service.refresh(RefreshSessionRequest(initial.sessionId, initial.refreshToken)) }
        val current = service.login(LoginRequest("qa@example.test", "test-password"))
        assertEquals(2L, current.user.authRevision)
        assertEquals(current.user, assertNotNull(sessions.find(current.sessionId)).user)
    }

    private fun withDatabase(block: suspend (MongoDatabase) -> Unit) = runBlocking {
        val uri = System.getenv("V12_TEST_MONGODB_URI")
        require(uri == "mongodb://127.0.0.1:27017") { "Integration tests require the isolated loopback Mongo service" }
        val name = "v12_test_" + UUID.randomUUID().toString().replace("-", "")
        MongoClient.create(uri).use { client ->
            val database = client.getDatabase(name)
            try { block(database) } finally { database.drop() }
        }
    }
}