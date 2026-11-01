package org.companerodeescuela.api.admin

import kotlinx.coroutines.test.runTest
import org.companerodeescuela.api.auth.AccountIdentityChange
import org.companerodeescuela.api.auth.InMemoryPlatformAccountRepository
import org.companerodeescuela.api.auth.PasswordHasher
import org.companerodeescuela.api.auth.PlatformAccount
import org.companerodeescuela.api.institutions.InMemoryInstitutionRepository
import org.companerodeescuela.api.security.InMemoryMfaRepository
import org.companerodeescuela.api.security.MfaSecretCipher
import org.companerodeescuela.api.security.MfaService
import org.companerodeescuela.api.security.Totp
import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.InstitutionSummary
import org.companerodeescuela.shared.contracts.StepUpAction
import org.companerodeescuela.shared.contracts.UserRole
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class QaAdminBootstrapTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-31T12:00:30Z"), ZoneOffset.UTC)
    private val institutions = InMemoryInstitutionRepository(listOf(InstitutionSummary("qa-institution", "QA-UNIV")))

    private suspend fun fixture(): Triple<InMemoryPlatformAccountRepository, MfaService, QaAdminBootstrapSpec> {
        val accounts = InMemoryPlatformAccountRepository()
        val password = "bootstrap-test-password"
        val actor =
            PlatformAccount(
                "candidate",
                "QA candidate",
                "qa@example.test",
                PasswordHasher().hash(password),
                setOf(UserRole.WORKSHOP_PARTICIPANT),
                emailVerifiedAt = clock.instant().minusSeconds(60),
            )
        accounts.create(actor)
        val enrollmentClock = Clock.fixed(clock.instant().minusSeconds(30), ZoneOffset.UTC)
        // The same durable MFA repository is used for enrollment and the fresh command.
        val repository = InMemoryMfaRepository()
        val key = MfaSecretCipher(ByteArray(32) { 7 })
        val enrolling = MfaService(accounts, repository, key, enrollmentClock)
        val enrollment = enrolling.beginEnrollment(actor, password)
        enrolling.confirmEnrollment(
            actor,
            enrollment.enrollmentId,
            Totp.code(Totp.decodeSecret(enrollment.secret), enrollmentClock.instant().epochSecond),
        )
        val active = MfaService(accounts, repository, key, clock)
        val spec =
            QaAdminBootstrapSpec("qa-institution", actor.id, 0, "bootstrap-operation", clock.instant().epochSecond + 600, "p".repeat(43))
        val proof =
            active.issueStepUp(
                actor,
                password,
                Totp.code(Totp.decodeSecret(enrollment.secret), clock.instant().epochSecond),
                StepUpAction.PRIVILEGED_INVITE_ACCEPT,
                spec.institutionId,
                spec.operationId,
                spec.digest(),
            )
        return Triple(
            accounts,
            active,
            QaAdminBootstrapSpec(spec.institutionId, spec.accountId, spec.expectedRevision, spec.operationId, spec.expiresAt, proof.proof),
        )
    }

    private class Claims : QaAdminBootstrapClaims {
        override suspend fun activateRegistration(
            institutionId: String,
            digest: String,
        ) = this.digest == digest

        private var digest: String? = null

        override suspend fun claim(
            institutionId: String,
            digest: String,
        ): Boolean {
            if (this.digest != null) return this.digest == digest
            this.digest = digest
            return true
        }
    }

    @Test
    fun `operator opt in and candidate MFA create one scoped audited admin and exact retry`() =
        runTest {
            val (accounts, mfa, spec) = fixture()
            val claims = Claims()
            repeat(2) { QaAdminBootstrap.provision(spec, "development", "companero_qa", accounts, institutions, mfa, claims, clock) }
            val account = requireNotNull(accounts.findById(spec.accountId))
            assertEquals(setOf(UserRole.ADMIN), account.roles)
            assertEquals(spec.institutionId, account.institutionId)
            assertEquals(1, account.identityAudit.size)
            assertEquals(spec.digest(), account.identityAudit.single().scopeDigest)
            assertTrue(account.adminCapabilities.isNotEmpty())
            accounts.changeIdentity(
                account.id,
                account.authRevision,
                AccountIdentityChange(
                    "suspend",
                    "operator",
                    AccountStatus.SUSPENDED,
                    account.roles,
                    account.institutionId,
                    clock.instant(),
                ),
            )
            assertFailsWith<IllegalArgumentException> {
                QaAdminBootstrap.provision(spec, "development", "companero_qa", accounts, institutions, mfa, claims, clock)
            }
        }

    @Test
    fun `production shared database and expired authorization never grant authority`() =
        runTest {
            val (accounts, mfa, spec) = fixture()
            for ((environment, database) in listOf("production" to "companero_qa", "development" to "companero_dev")) {
                assertFailsWith<IllegalArgumentException> {
                    QaAdminBootstrap.provision(spec, environment, database, accounts, institutions, mfa, Claims(), clock)
                }
            }
            val expired =
                QaAdminBootstrapSpec(spec.institutionId, spec.accountId, 0, spec.operationId, clock.instant().epochSecond, spec.proof)
            assertFailsWith<IllegalArgumentException> {
                QaAdminBootstrap.provision(expired, "development", "companero_qa", accounts, institutions, mfa, Claims(), clock)
            }
            assertEquals(setOf(UserRole.WORKSHOP_PARTICIPANT), accounts.findById(spec.accountId)?.roles)
        }

    @Test
    fun `changed command cannot reuse fresh MFA and existing administrators block bootstrap`() =
        runTest {
            val (accounts, mfa, spec) = fixture()
            val altered = QaAdminBootstrapSpec(spec.institutionId, spec.accountId, 0, "another-operation", spec.expiresAt, spec.proof)
            assertFailsWith<org.companerodeescuela.api.errors.ApiException.Forbidden> {
                QaAdminBootstrap.provision(altered, "development", "companero_qa", accounts, institutions, mfa, Claims(), clock)
            }
            accounts.create(
                PlatformAccount(
                    "admin",
                    "Existing admin",
                    "admin@example.test",
                    "unused",
                    setOf(UserRole.ADMIN),
                    institutionId = spec.institutionId,
                ),
            )
            assertFailsWith<IllegalArgumentException> {
                QaAdminBootstrap.provision(spec, "development", "companero_qa", accounts, institutions, mfa, Claims(), clock)
            }
        }

    @Test
    fun `disabled configuration is inert and never renders proof`() {
        assertEquals(null, QaAdminBootstrap.parse { null })
        assertFailsWith<IllegalArgumentException> { QaAdminBootstrap.parse { "invalid" } }
        assertEquals("QaAdminBootstrapSpec([redacted])", QaAdminBootstrapSpec("qa", "candidate", 0, "operation", 1, "private").toString())
    }
}
