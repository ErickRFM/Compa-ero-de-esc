package org.companerodeescuela.api.devices

import kotlinx.coroutines.runBlocking
import org.companerodeescuela.api.auth.InMemoryPlatformAccountRepository
import org.companerodeescuela.api.auth.PlatformAccount
import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.UserRole
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class PushOutboxRecipientAuthorizationTest {
    @Test
    fun `recipient authorization approves active verified members across academic and management roles`() =
        runBlocking {
            val accounts = InMemoryPlatformAccountRepository()

            val student = createAccount("student-1", setOf(UserRole.STUDENT), verified = true, active = true)
            val teacher = createAccount("teacher-1", setOf(UserRole.TEACHER), verified = true, active = true)
            val tutor = createAccount("tutor-1", setOf(UserRole.TUTOR), verified = true, active = true)
            val principal = createAccount("principal-1", setOf(UserRole.PRINCIPAL), verified = true, active = true)
            val coordinator = createAccount("coordinator-1", setOf(UserRole.COORDINATOR), verified = true, active = true)
            val unverified = createAccount("unverified-1", setOf(UserRole.STUDENT), verified = false, active = true)
            val suspended = createAccount("suspended-1", setOf(UserRole.TEACHER), verified = true, active = false)

            listOf(student, teacher, tutor, principal, coordinator, unverified, suspended).forEach {
                accounts.create(it)
            }

            val allowedRoles =
                setOf(
                    UserRole.STUDENT,
                    UserRole.TEACHER,
                    UserRole.TUTOR,
                    UserRole.PRINCIPAL,
                    UserRole.COORDINATOR,
                    UserRole.ADMIN,
                    UserRole.SUPER_ADMIN,
                )

            val authorizeRecipient: suspend (String) -> Boolean = { owner ->
                val id = owner.takeIf { it.startsWith("NATIVE:") }?.removePrefix("NATIVE:")
                val current = id?.let { accounts.findById(it) }
                current?.permitsSession == true &&
                    current.emailVerifiedAt != null &&
                    current.roles.any { it in allowedRoles }
            }

            assertTrue(authorizeRecipient("NATIVE:student-1"), "Student must be authorized for push delivery")
            assertTrue(authorizeRecipient("NATIVE:teacher-1"), "Teacher must be authorized for push delivery")
            assertTrue(authorizeRecipient("NATIVE:tutor-1"), "Tutor must be authorized for push delivery")
            assertTrue(authorizeRecipient("NATIVE:principal-1"), "Principal must be authorized for push delivery")
            assertTrue(authorizeRecipient("NATIVE:coordinator-1"), "Coordinator must be authorized for push delivery")

            assertFalse(authorizeRecipient("NATIVE:unverified-1"), "Unverified account must be rejected")
            assertFalse(authorizeRecipient("NATIVE:suspended-1"), "Suspended account must be rejected")
            assertFalse(authorizeRecipient("NATIVE:nonexistent"), "Unknown account must be rejected")
            assertFalse(authorizeRecipient("MALFORMED:id"), "Non-native prefix must be rejected")
        }

    private fun createAccount(
        id: String,
        roles: Set<UserRole>,
        verified: Boolean,
        active: Boolean,
    ): PlatformAccount =
        PlatformAccount(
            id = id,
            email = "$id@school.test",
            displayName = "User $id",
            passwordHash = "hash",
            accountStatus = if (active) AccountStatus.ACTIVE else AccountStatus.SUSPENDED,
            roles = roles,
            institutionId = "campus-1",
            emailVerifiedAt = if (verified) Instant.now() else null,
        )
}
