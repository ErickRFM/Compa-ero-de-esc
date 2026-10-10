package org.companerodeescuela.api.auth

import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.Timeout
import kotlin.test.*
import org.companerodeescuela.shared.contracts.*

/** Concurrency uses independent repositories against the isolated CI Mongo service. */
@EnabledIfEnvironmentVariable(named = "V12_TEST_MONGODB_URI", matches = ".+")
@Timeout(120)
class MongoEmailVerificationIntegrationTest {
    private val now = Instant.parse("2026-10-10T00:00:00Z")
    private val hash = "A".repeat(43)
    private suspend fun pending(repo: PlatformAccountRepository) {
        assertTrue(repo.create(PlatformAccount("account", "QA", "qa@example.test", "test-only-hash", emptySet(),
            accountStatus = AccountStatus.PENDING_VERIFICATION, registrationIntent = RegistrationIntent(RegistrationAccountType.STUDENT))))
    }
    @Test fun `real Mongo confirmation grants once across independent repositories`() = database { db ->
        val first = MongoPlatformAccountRepository(db)
        val second = MongoPlatformAccountRepository(db)
        pending(first)
        assertTrue(first.issueEmailVerification("account", 0, hash, now, now.plusSeconds(3600)))
        val results = coroutineScope { (1..24).map { n -> async {
            (if (n % 2 == 0) first else second).confirmEmailVerification("account", hash, now.plusSeconds(1))
        } }.awaitAll() }
        assertEquals(1, results.count { it != null })
        val current = assertNotNull(second.findById("account"))
        assertEquals(AccountStatus.ACTIVE, current.accountStatus)
        assertEquals(setOf(UserRole.STUDENT), current.roles)
        assertEquals(1L, current.authRevision)
        assertEquals(1, current.identityAudit.size)
        assertNull(current.emailVerification)
        assertNotNull(current.emailVerifiedAt)
    }
    @Test fun `real Mongo simultaneous resends and hourly quota survive repository recreation`() = database { db ->
        val first = MongoPlatformAccountRepository(db)
        pending(first)
        assertTrue(first.issueEmailVerification("account", 0, hash, now, now.plusSeconds(3600)))
        val results = coroutineScope { (1..24).map { n -> async {
            MongoPlatformAccountRepository(db).issueEmailVerification("account", 0, (if (n % 2 == 0) "B" else "C").repeat(43),
                now.plusSeconds(61), now.plusSeconds(3661))
        } }.awaitAll() }
        assertEquals(1, results.count { it })
        val restored = MongoPlatformAccountRepository(db)
        assertTrue(restored.issueEmailVerification("account", 0, "D".repeat(43), now.plusSeconds(122), now.plusSeconds(3722)))
        assertFalse(restored.issueEmailVerification("account", 0, hash, now.plusSeconds(183), now.plusSeconds(3783)))
        assertTrue(restored.issueEmailVerification("account", 0, hash, now.plusSeconds(3601), now.plusSeconds(7201)))
    }
    @Test fun `real Mongo suspension restoration invalidates previous email challenge`() = database { db ->
        val repo = MongoPlatformAccountRepository(db)
        pending(repo)
        assertTrue(repo.issueEmailVerification("account", 0, hash, now, now.plusSeconds(3600)))
        assertNotNull(repo.changeIdentity("account", 0, AccountIdentityChange("suspend", "admin", AccountStatus.SUSPENDED, emptySet(), null, now)))
        assertNotNull(repo.changeIdentity("account", 1, AccountIdentityChange("restore", "admin", AccountStatus.PENDING_VERIFICATION, emptySet(), null, now)))
        assertNull(MongoPlatformAccountRepository(db).confirmEmailVerification("account", hash, now.plusSeconds(1)))
        assertNull(repo.findById("account")?.emailVerifiedAt)
        assertTrue(repo.issueEmailVerification("account", 2, "B".repeat(43), now.plusSeconds(61), now.plusSeconds(3661)))
        assertEquals(3L, assertNotNull(repo.confirmEmailVerification("account", "B".repeat(43), now.plusSeconds(62))).authRevision)
    }
    private fun database(block: suspend (MongoDatabase) -> Unit) = runBlocking {
        val uri = System.getenv("V12_TEST_MONGODB_URI")
        require(uri == "mongodb://127.0.0.1:27017") { "Integration tests require the isolated loopback Mongo service" }
        MongoClient.create(uri).use { client ->
            val db = client.getDatabase("v12_test_" + UUID.randomUUID().toString().replace("-", ""))
            try { block(db) } finally { db.drop() }
        }
    }
}
