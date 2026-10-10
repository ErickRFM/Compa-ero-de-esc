package org.companerodeescuela.api.auth

import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.UserRole

class PlatformAccountLifecycleTest {
    private val now = Instant.parse("2026-10-09T12:00:00Z")
    private fun account(id: String = "account-1", email: String = "qa@example.test") =
        PlatformAccount(id, "QA", email, "test-only-hash", setOf(UserRole.TEACHER_PENDING), createdAt = now)
    private fun change(operation: String = "approve-1", status: AccountStatus = AccountStatus.ACTIVE) =
        AccountIdentityChange(operation, "admin-1", status, setOf(UserRole.TEACHER), "institution-a", now)

    @Test
    fun `one CAS winner and exact retry append one secret-free audit entry`() = runTest {
        val accounts = InMemoryPlatformAccountRepository()
        accounts.create(account())
        val change = change()
        val results = coroutineScope { (1..16).map { n ->
            async { accounts.changeIdentity("account-1", 0, change.copy(operationId = "approve-$n")) }
        }.awaitAll() }
        assertEquals(1, results.count { it != null })
        val updated = assertNotNull(accounts.findById("account-1"))
        assertEquals(1L, updated.authRevision)
        assertEquals(setOf(UserRole.TEACHER), updated.roles)
        assertEquals("institution-a", updated.institutionId)
        assertEquals(1, updated.identityAudit.size)
        val event = updated.identityAudit.single()
        val retried = assertNotNull(accounts.changeIdentity("account-1", 0, change.copy(operationId = event.operationId)))
        assertEquals(updated, retried)
        assertEquals("admin-1", event.actorId)
        assertEquals(0L, event.fromRevision)
        assertEquals(1L, event.toRevision)
        assertFalse(event.toString().contains("qa@example.test"))
        assertFalse(event.toString().contains("test-only-hash"))
    }

    @Test
    fun `request id reuse with a different actor or grant is rejected`() = runTest {
        val accounts = InMemoryPlatformAccountRepository()
        accounts.create(account())
        assertNotNull(accounts.changeIdentity("account-1", 0, change()))
        assertNull(accounts.changeIdentity("account-1", 0, change().copy(actorId = "other-admin")))
        assertNull(accounts.changeIdentity("account-1", 0, change().copy(roles = setOf(UserRole.SUPER_ADMIN))))
        assertNull(accounts.changeIdentity("account-1", 1, change()))
        assertEquals(1, assertNotNull(accounts.findById("account-1")).identityAudit.size)
    }

    @Test
    fun `revoked identity is terminal and stale updates never overwrite newer authority`() = runTest {
        val accounts = InMemoryPlatformAccountRepository()
        accounts.create(account())
        val revoked = assertNotNull(accounts.changeIdentity("account-1", 0, change("revoke-1", AccountStatus.REVOKED)))
        assertFalse(revoked.permitsSession)
        assertFalse(revoked.active)
        assertNull(accounts.changeIdentity("account-1", 1, change("reactivate-1")))
        assertNull(accounts.changeIdentity("account-1", 0, change("stale-1")))
        assertEquals(revoked, accounts.findById("account-1"))
    }

    @Test
    fun `suspend and reinstate increment revisions without discarding history`() = runTest {
        val accounts = InMemoryPlatformAccountRepository()
        accounts.create(account())
        val suspended = assertNotNull(accounts.changeIdentity("account-1", 0, change("suspend-1", AccountStatus.SUSPENDED)))
        assertFalse(suspended.permitsSession)
        val restored = assertNotNull(accounts.changeIdentity("account-1", 1, change("restore-1")))
        assertEquals(2L, restored.authRevision)
        assertEquals(2, restored.identityAudit.size)
    }

    @Test
    fun `concurrent normalized email registration creates exactly one account`() {
        val pool = Executors.newFixedThreadPool(16)
        try {
            repeat(30) { round ->
                val accounts = InMemoryPlatformAccountRepository()
                val ready = CountDownLatch(16)
                val start = CountDownLatch(1)
                val futures = (1..16).map { n -> pool.submit<Boolean> {
                    ready.countDown()
                    check(start.await(10, TimeUnit.SECONDS))
                    runBlocking { accounts.create(account("account-$round-$n", if (n % 2 == 0) " QA@EXAMPLE.TEST " else "qa@example.test")) }
                } }
                check(ready.await(10, TimeUnit.SECONDS))
                start.countDown()
                assertEquals(1, futures.count { it.get(10, TimeUnit.SECONDS) }, "round $round")
                assertEquals("qa@example.test", assertNotNull(runBlocking { accounts.findByIdentifier(" QA@EXAMPLE.TEST ") }).email)
            }
        } finally { pool.shutdownNow() }
    }
}