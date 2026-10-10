package org.companerodeescuela.api.auth

import java.time.Instant
import kotlin.test.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.RegistrationAccountType
import org.companerodeescuela.shared.contracts.UserRole

class EmailVerificationRepositoryTest {
    private val now=Instant.parse("2026-10-10T04:00:00Z")
    private val hash="A".repeat(43)

    @Test fun `concurrent confirmation consumes one token and records one authority transition`() = runTest {
        repeat(8) {
            val repository=pendingRepository()
            assertTrue(repository.issueEmailVerification("pending",0,hash,now,now.plusSeconds(3600)))
            val outcomes=coroutineScope { (1..24).map { async(Dispatchers.Default) { repository.confirmEmailVerification("pending",hash,now.plusSeconds(1)) } }.awaitAll() }
            assertEquals(1,outcomes.count { it!=null })
            val account=assertNotNull(repository.findById("pending"))
            assertEquals(AccountStatus.ACTIVE,account.accountStatus)
            assertEquals(setOf(UserRole.STUDENT),account.roles)
            assertEquals(1L,account.authRevision)
            assertEquals(1,account.identityAudit.size)
            assertNull(account.emailVerification)
            assertNotNull(account.emailVerifiedAt)
        }
    }

    @Test fun `concurrent resend has one winner and three per hour survives repository reads`() = runTest {
        val repository=pendingRepository()
        assertTrue(repository.issueEmailVerification("pending",0,hash,now,now.plusSeconds(3600)))
        assertFalse(repository.issueEmailVerification("pending",0,"B".repeat(43),now.plusSeconds(59),now.plusSeconds(3659)))
        val outcomes=coroutineScope { (1..24).map { async(Dispatchers.Default) { repository.issueEmailVerification("pending",0,"B".repeat(43),now.plusSeconds(61),now.plusSeconds(3661)) } }.awaitAll() }
        assertEquals(1,outcomes.count { it })
        assertTrue(repository.issueEmailVerification("pending",0,"C".repeat(43),now.plusSeconds(122),now.plusSeconds(3722)))
        assertFalse(repository.issueEmailVerification("pending",0,"D".repeat(43),now.plusSeconds(183),now.plusSeconds(3783)))
        assertTrue(repository.issueEmailVerification("pending",0,"E".repeat(43),now.plusSeconds(3601),now.plusSeconds(7201)))
        assertNull(repository.confirmEmailVerification("pending",hash,now.plusSeconds(3602)))
    }

    @Test fun `suspended or revoked account cannot confirm or rotate an old challenge`() = runTest {
        for(status in listOf(AccountStatus.SUSPENDED,AccountStatus.REVOKED)) {
            val repository=pendingRepository()
            assertTrue(repository.issueEmailVerification("pending",0,hash,now,now.plusSeconds(3600)))
            repository.changeIdentity("pending",0,AccountIdentityChange("block","operator",status,emptySet(),null,now.plusSeconds(1)))
            assertNull(repository.confirmEmailVerification("pending",hash,now.plusSeconds(2)))
            assertFalse(repository.issueEmailVerification("pending",0,hash,now.plusSeconds(61),now.plusSeconds(3661)))
            val account=assertNotNull(repository.findById("pending"))
            assertEquals(status,account.accountStatus)
            assertNull(account.emailVerifiedAt)
            assertTrue(account.roles.isEmpty())
        }
    }

    @Test fun `expired or malformed challenge never changes account authority`() = runTest {
        val repository=pendingRepository()
        assertTrue(repository.issueEmailVerification("pending",0,hash,now,now.plusSeconds(3600)))
        assertNull(repository.confirmEmailVerification("pending","wrong",now.plusSeconds(1)))
        assertNull(repository.confirmEmailVerification("pending",hash,now.plusSeconds(3600)))
        assertEquals(0L,repository.findById("pending")?.authRevision)
        assertTrue(assertNotNull(repository.findById("pending")).identityAudit.isEmpty())
    }

    private suspend fun pendingRepository()=InMemoryPlatformAccountRepository().also {
        assertTrue(it.create(PlatformAccount("pending","QA","qa@example.test","unused-test-hash",emptySet(),
            accountStatus=AccountStatus.PENDING_VERIFICATION,registrationIntent=RegistrationIntent(RegistrationAccountType.STUDENT))))
    }
}