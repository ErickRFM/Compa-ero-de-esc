package org.companerodeescuela.api.institutions

import kotlinx.coroutines.test.runTest
import org.companerodeescuela.api.auth.InMemoryPlatformAccountRepository
import org.companerodeescuela.api.auth.PasswordHasher
import org.companerodeescuela.api.auth.PlatformAccount
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.security.InMemoryMfaRepository
import org.companerodeescuela.api.security.MfaSecretCipher
import org.companerodeescuela.api.security.MfaService
import org.companerodeescuela.api.security.Totp
import org.companerodeescuela.shared.contracts.StepUpAction
import org.companerodeescuela.shared.contracts.UserRole
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class InstitutionOwnerProvisionServiceTest {
    private val now = Instant.parse("2026-11-06T15:00:30Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val command = InstitutionProvisionCommand("UPTLAX", "Universidad Politecnica", "operation-001")

    @Test
    fun `only verified hotmail super administrator may prepare institution enablement`() =
        runTest {
            val accounts = InMemoryPlatformAccountRepository()
            val institutions = InMemoryInstitutionRepository()
            val service =
                InstitutionOwnerProvisionService(
                    accounts,
                    institutions,
                    MfaService(accounts, InMemoryMfaRepository(), null, clock),
                    clock,
                )
            val attacker =
                PlatformAccount(
                    "a",
                    "Other admin",
                    "other@example.test",
                    "hash",
                    setOf(UserRole.SUPER_ADMIN),
                    emailVerifiedAt = now.minusSeconds(10),
                )
            val unprivileged =
                attacker.copy(
                    id = "b",
                    email = "erik55509@hotmail.com",
                    roles = setOf(UserRole.STUDENT),
                )
            accounts.create(attacker)
            accounts.create(unprivileged)
            assertFailsWith<ApiException.Forbidden> { service.prepare(attacker, command) }
            assertFailsWith<ApiException.Forbidden> { service.prepare(unprivileged, command) }
        }

    @Test
    fun `owner creates a durable enabled institution only with bound MFA and an unused id`() =
        runTest {
            val accounts = InMemoryPlatformAccountRepository()
            val institutions = InMemoryInstitutionRepository()
            val password = "owner-test-password"
            val owner =
                PlatformAccount(
                    "owner",
                    "Owner",
                    "erik55509@hotmail.com",
                    PasswordHasher().hash(password),
                    setOf(UserRole.SUPER_ADMIN),
                    emailVerifiedAt = now.minusSeconds(60),
                )
            accounts.create(owner)
            val factors = InMemoryMfaRepository()
            val cipher = MfaSecretCipher(ByteArray(32) { 1 })
            val enrollmentClock = Clock.fixed(now.minusSeconds(60), ZoneOffset.UTC)
            val enroll = MfaService(accounts, factors, cipher, enrollmentClock)
            val pending = enroll.beginEnrollment(owner, password)
            enroll.confirmEnrollment(
                owner,
                pending.enrollmentId,
                Totp.code(Totp.decodeSecret(pending.secret), enrollmentClock.instant().epochSecond),
            )
            val mfa = MfaService(accounts, factors, cipher, clock)
            val service = InstitutionOwnerProvisionService(accounts, institutions, mfa, clock)
            val digest = service.prepare(owner, command)
            assertFailsWith<ApiException.Forbidden> { service.create(owner, command, "invalid") }
            val proof =
                mfa.issueStepUp(
                    owner,
                    password,
                    Totp.code(Totp.decodeSecret(pending.secret), now.epochSecond),
                    StepUpAction.INSTITUTION_MANAGEMENT,
                    InstitutionOwnerProvisionService.RESOURCE_ID,
                    command.operationId,
                    digest,
                )
            val created = service.create(owner, command, proof.proof)
            assertEquals("UPTLAX", created.id)
            assertEquals("Universidad Politecnica", assertNotNull(institutions.findEnabled("UPTLAX")).displayName)
            assertFailsWith<ApiException.Forbidden> { service.create(owner, command, proof.proof) }
        }
}
