package org.companerodeescuela.api.auth

import java.time.Instant
import java.util.Date
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.bson.Document
import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.UserRole

class MongoAccountLifecycleTest {
    private val now = Instant.parse("2026-10-09T12:00:00Z")
    private fun fixture() = MongoAuthFixture(Document("_id", "account-1")
        .append("displayName", "QA").append("email", "qa@example.test").append("emailNormalized", "qa@example.test")
        .append("passwordHash", "test-only-hash").append("roles", listOf("TEACHER_PENDING"))
        .append("createdAt", Date.from(now)).append("active", true))
    private fun change(operation: String = "approve-1", status: AccountStatus = AccountStatus.ACTIVE) =
        AccountIdentityChange(operation, "admin-1", status, setOf(UserRole.TEACHER), "institution-a", now)

    @Test
    fun `legacy document mutation atomically persists authority and a secret-free audit`() = runTest {
        val fixture = fixture()
        val accounts = MongoPlatformAccountRepository(fixture.database)
        val updated = assertNotNull(accounts.changeIdentity("account-1", 0, change()))
        assertEquals(1L, updated.authRevision)
        assertEquals("institution-a", updated.institutionId)
        assertEquals(setOf(UserRole.TEACHER), updated.roles)
        assertEquals(1, updated.identityAudit.size)
        assertEquals(updated, accounts.changeIdentity("account-1", 0, change()))
        assertNull(accounts.changeIdentity("account-1", 0, change("other-operation")))
        assertNull(accounts.changeIdentity("account-1", 0, change().copy(roles = setOf(UserRole.SUPER_ADMIN))))
        val auditJson = fixture.document.getList("identityAudit", Document::class.java).single().toJson()
        assertFalse("test-only-hash" in auditJson)
        assertFalse("qa@example.test" in auditJson)
    }

    @Test
    fun `Mongo CAS cannot overwrite an identity changed after its initial lookup`() = runTest {
        val fixture = fixture()
        val accounts = MongoPlatformAccountRepository(fixture.database)
        fixture.beforeNextFindOneAndUpdate = {
            fixture.document["authRevision"] = 1L
            fixture.document["accountStatus"] = "REVOKED"
            fixture.document["active"] = false
        }
        assertNull(accounts.changeIdentity("account-1", 0, change()))
        val current = assertNotNull(accounts.findById("account-1"))
        assertEquals(AccountStatus.REVOKED, current.accountStatus)
        assertEquals(1L, current.authRevision)
        assertEquals(emptyList(), current.identityAudit)
    }

    @Test
    fun `revoked and malformed authority records cannot be reactivated through CAS`() = runTest {
        for (status in listOf("REVOKED", "unknown", null)) {
            val fixture = fixture()
            fixture.document["accountStatus"] = status
            val accounts = MongoPlatformAccountRepository(fixture.database)
            assertNull(accounts.changeIdentity("account-1", 0, change()))
        }
        val fixture = fixture()
        fixture.document["authRevision"] = "0"
        assertNull(MongoPlatformAccountRepository(fixture.database).changeIdentity("account-1", 0, change()))
    }
}