package org.companerodeescuela.api.integrations.push

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.companerodeescuela.api.auth.InMemoryPlatformAccountRepository
import org.companerodeescuela.api.auth.PasswordHasher
import org.companerodeescuela.api.auth.PlatformAccount
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.institutions.InMemoryInstitutionRepository
import org.companerodeescuela.api.security.InMemoryMfaRepository
import org.companerodeescuela.api.security.MfaSecretCipher
import org.companerodeescuela.api.security.MfaService
import org.companerodeescuela.api.security.Totp
import org.companerodeescuela.shared.contracts.AdminCapability
import org.companerodeescuela.shared.contracts.InstitutionSummary
import org.companerodeescuela.shared.contracts.InstitutionalIdentityLinkRequest
import org.companerodeescuela.shared.contracts.InstitutionalIdentityKind
import org.companerodeescuela.shared.contracts.StepUpAction
import org.companerodeescuela.shared.contracts.UserRole

class InstitutionalIdentityLinkServiceTest {
    private class TestClock : Clock() {
        var current = Instant.parse("2026-10-15T12:00:00Z")
        override fun instant() = current
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = this
    }

    @Test
    fun `approved link resolves only an existing verified account in the same institution`() = runBlocking {
        val clock = TestClock()
        val accounts = InMemoryPlatformAccountRepository()
        val directory = InMemoryInstitutionRepository(listOf(InstitutionSummary("campus", "QA Campus")))
        val mfa = MfaService(accounts, InMemoryMfaRepository(), MfaSecretCipher(ByteArray(32) { it.toByte() }), clock)
        val service = InstitutionalIdentityLinkService(
            accounts = accounts,
            institutions = directory,
            repository = InMemoryInstitutionalIdentityLinkRepository(),
            mfa = mfa,
            clock = clock,
        )
        val admin = PlatformAccount(
            id = "admin",
            displayName = "QA Admin",
            email = "admin@example.test",
            passwordHash = PasswordHasher().hash("operator-test-password"),
            roles = setOf(UserRole.ADMIN),
            institutionId = "campus",
            emailVerifiedAt = clock.current,
            adminCapabilities = setOf(AdminCapability.MANAGE_ACADEMICS),
        )
        val student = PlatformAccount(
            id = "student-1",
            displayName = "Existing Student",
            email = "student@example.test",
            passwordHash = "already-hashed",
            roles = setOf(UserRole.STUDENT),
            institutionId = "campus",
            emailVerifiedAt = clock.current,
        )
        accounts.create(admin)
        accounts.create(student)
        val enrollment = mfa.beginEnrollment(admin, "operator-test-password")
        val seed = Totp.decodeSecret(enrollment.secret)
        clock.current = clock.current.plusSeconds(30)
        mfa.confirmEnrollment(admin, enrollment.enrollmentId, Totp.code(seed, clock.current.epochSecond))

        val request = InstitutionalIdentityLinkRequest(
            institutionId = "campus",
            entityType = InstitutionalIdentityKind.STUDENT,
            externalId = "SIS-0091",
            nativeUserId = student.id,
            operationId = "link-0091",
            proof = "",
        )
        val digest = org.companerodeescuela.shared.contracts.InstitutionalIdentityLinkDigest.command(request)
        clock.current = clock.current.plusSeconds(30)
        val proof = mfa.issueStepUp(
            admin,
            "operator-test-password",
            Totp.code(seed, clock.current.epochSecond),
            StepUpAction.MANAGE_ACADEMICS,
            "campus",
            request.operationId,
            digest,
        )

        val linked = service.link(admin.toUserSummary(), request.copy(proof = proof.proof))
        assertEquals("student-1", service.resolve("campus", InstitutionalIdentityKind.STUDENT, "SIS-0091")?.id)
        assertEquals("student-1", linked.nativeUserId)
        assertEquals(linked, service.link(admin.toUserSummary(), request.copy(proof = proof.proof)))
        assertFailsWith<ApiException.Conflict> {
            service.link(admin.toUserSummary(), request.copy(nativeUserId = "other-student", proof = "invalid"))
        }
        assertEquals(null, service.resolve("campus", InstitutionalIdentityKind.STUDENT, "SIS-UNKNOWN"))
    }
}
