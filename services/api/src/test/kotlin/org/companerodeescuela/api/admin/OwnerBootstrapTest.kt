package org.companerodeescuela.api.admin

import kotlinx.coroutines.test.runTest
import org.companerodeescuela.api.auth.AccountIdentityChange
import org.companerodeescuela.api.auth.InMemoryPlatformAccountRepository
import org.companerodeescuela.api.auth.PasswordHasher
import org.companerodeescuela.api.auth.PlatformAccount
import org.companerodeescuela.api.auth.RegistrationIntent
import org.companerodeescuela.api.security.InMemoryMfaRepository
import org.companerodeescuela.api.security.MfaSecretCipher
import org.companerodeescuela.api.security.MfaService
import org.companerodeescuela.api.security.Totp
import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.RegistrationAccountType
import org.companerodeescuela.shared.contracts.StepUpAction
import org.companerodeescuela.shared.contracts.UserRole
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class OwnerBootstrapTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-02T12:00:30Z"), ZoneOffset.UTC)

    @Test fun `startup opt in is disabled by default and never logs proof`() {
        assertEquals(null, OwnerBootstrap.parse { null })
        assertFailsWith<IllegalArgumentException> { OwnerBootstrap.parse { "invalid" } }
        assertEquals(
            "OwnerBootstrapSpec([redacted])",
            OwnerBootstrapSpec("x", "a@b", 0, "op", "operator", "test reason", 1, "secret").toString(),
        )
    }

    @Test fun `production owner is opt in and requires isolation attestation`() {
        assertEquals(null, OwnerBootstrap.parseProduction { null })
        assertFailsWith<IllegalArgumentException> {
            OwnerBootstrap.parseProduction { key ->
                when (key) {
                    "PROD_OWNER_BOOTSTRAP_ENABLED" -> "true"
                    "PROD_OWNER_ISOLATION_CERTIFIED" -> "false"
                    else -> null
                }
            }
        }
    }

    @Test fun `production cannot use a QA scoped command or proof`() =
        runTest {
            val (accounts, mfa, qaSpec) = fixture()
            assertFailsWith<IllegalArgumentException> {
                OwnerBootstrap.provision(
                    qaSpec,
                    "production",
                    "companero_prod",
                    accounts,
                    mfa,
                    InMemoryOwnerBootstrapClaims(),
                    clock,
                )
            }
            assertFailsWith<IllegalArgumentException> {
                OwnerBootstrap.provision(
                    qaSpec.copy(scope = "prod"),
                    "production",
                    "companero_qa",
                    accounts,
                    mfa,
                    InMemoryOwnerBootstrapClaims(),
                    clock,
                )
            }
            assertFailsWith<org.companerodeescuela.api.errors.ApiException.Forbidden> {
                OwnerBootstrap.provision(
                    qaSpec.copy(scope = "prod"),
                    "production",
                    "companero_prod",
                    accounts,
                    mfa,
                    InMemoryOwnerBootstrapClaims(),
                    clock,
                )
            }
            assertEquals(setOf(UserRole.STUDENT), assertNotNull(accounts.findById("owner")).roles)
        }

    @Test fun `verified invited owner becomes only SUPER_ADMIN after production MFA`() =
        runTest {
            val (accounts, mfa, spec) = fixture(scope = "prod")
            val claims = InMemoryOwnerBootstrapClaims()
            OwnerBootstrap.provision(spec, "production", "companero_prod", accounts, mfa, claims, clock)
            val owner = assertNotNull(accounts.findById("owner"))
            assertEquals(setOf(UserRole.SUPER_ADMIN), owner.roles)
            assertEquals("prod-declared:operator-qa", owner.identityAudit.single().actorId)
            assertEquals(spec.digest(), claims.find()?.digest)
        }

    @Test fun `verified student gains only the additional owner role through scoped MFA`() =
        runTest {
            val (accounts, mfa, spec) = fixture()
            val claims = InMemoryOwnerBootstrapClaims()
            OwnerBootstrap.provision(spec, "staging", "companero_qa", accounts, mfa, claims, clock)
            val account = assertNotNull(accounts.findById("owner"))
            assertEquals(setOf(UserRole.STUDENT, UserRole.SUPER_ADMIN), account.roles)
            assertEquals("institution", account.institutionId)
            assertEquals(1L, account.authRevision)
            assertEquals("qa-declared:operator-qa", account.identityAudit.single().actorId)
            assertEquals(spec.digest(), account.identityAudit.single().scopeDigest)
            assertEquals("QA owner enrollment", claims.find()?.reason)
            OwnerBootstrap.provision(spec, "staging", "companero_qa", accounts, mfa, claims, clock)
            assertEquals(1, assertNotNull(accounts.findById("owner")).identityAudit.size)
            val afterExpiry = Clock.fixed(clock.instant().plusSeconds(1800), ZoneOffset.UTC)
            OwnerBootstrap.provision(spec, "staging", "companero_qa", accounts, mfa, claims, afterExpiry)
            assertEquals(1, assertNotNull(accounts.findById("owner")).identityAudit.size)
        }

    @Test fun `production, shared databases, and altered MFA payload cannot grant owner authority`() =
        runTest {
            val (accounts, mfa, spec) = fixture()
            for ((environment, database) in listOf("production" to "companero_qa", "staging" to "companero_dev")) {
                assertFailsWith<IllegalArgumentException> {
                    OwnerBootstrap.provision(spec, environment, database, accounts, mfa, InMemoryOwnerBootstrapClaims(), clock)
                }
            }
            assertFailsWith<org.companerodeescuela.api.errors.ApiException.Forbidden> {
                OwnerBootstrap.provision(
                    spec.copy(reason = "changed reason"),
                    "staging",
                    "companero_qa",
                    accounts,
                    mfa,
                    InMemoryOwnerBootstrapClaims(),
                    clock,
                )
            }
            assertEquals(setOf(UserRole.STUDENT), assertNotNull(accounts.findById("owner")).roles)
        }

    @Test fun `expired proof and revoked role cannot be restored by bootstrap retry`() =
        runTest {
            val (accounts, mfa, spec) = fixture()
            assertFailsWith<IllegalArgumentException> {
                OwnerBootstrap.provision(
                    spec.copy(expiresAt = clock.instant().epochSecond),
                    "staging",
                    "companero_qa",
                    accounts,
                    mfa,
                    InMemoryOwnerBootstrapClaims(),
                    clock,
                )
            }
            val claims = InMemoryOwnerBootstrapClaims()
            OwnerBootstrap.provision(spec, "staging", "companero_qa", accounts, mfa, claims, clock)
            assertNotNull(
                accounts.changeIdentity(
                    "owner",
                    1,
                    AccountIdentityChange(
                        "revoke",
                        "operator",
                        AccountStatus.ACTIVE,
                        setOf(UserRole.STUDENT),
                        "institution",
                        clock.instant(),
                    ),
                ),
            )
            assertFailsWith<IllegalArgumentException> {
                OwnerBootstrap.provision(
                    spec,
                    "staging",
                    "companero_qa",
                    accounts,
                    mfa,
                    claims,
                    clock,
                )
            }
            assertEquals(setOf(UserRole.STUDENT), assertNotNull(accounts.findById("owner")).roles)
        }

    @Test fun `interrupted reservation can resume the same authorized command before proof expires`() =
        runTest {
            val (accounts, mfa, spec) = fixture()
            val durable = InMemoryOwnerBootstrapClaims()
            var failOnce = true
            val interrupted =
                object : OwnerBootstrapClaims {
                    override suspend fun find() = durable.find()

                    override suspend fun reserve(claim: OwnerBootstrapClaim): Boolean {
                        val reserved = durable.reserve(claim)
                        if (failOnce) {
                            failOnce = false
                            error("simulated interruption")
                        }
                        return reserved
                    }
                }
            assertFailsWith<IllegalStateException> {
                OwnerBootstrap.provision(spec, "staging", "companero_qa", accounts, mfa, interrupted, clock)
            }
            assertEquals(setOf(UserRole.STUDENT), assertNotNull(accounts.findById("owner")).roles)
            OwnerBootstrap.provision(spec, "staging", "companero_qa", accounts, mfa, interrupted, clock)
            assertEquals(setOf(UserRole.STUDENT, UserRole.SUPER_ADMIN), assertNotNull(accounts.findById("owner")).roles)
        }

    private suspend fun fixture(scope: String = "qa"): Triple<InMemoryPlatformAccountRepository, MfaService, OwnerBootstrapSpec> {
        val accounts = InMemoryPlatformAccountRepository()
        val password = "qa-test-password"
        val account =
            PlatformAccount(
                "owner",
                "Owner",
                "owner@example.test",
                PasswordHasher().hash(password),
                if (scope == "prod") setOf(UserRole.WORKSHOP_PARTICIPANT) else setOf(UserRole.STUDENT),
                institutionId = "institution",
                registrationIntent = if (scope == "prod") RegistrationIntent(RegistrationAccountType.OWNER_INVITED) else null,
                emailVerifiedAt = clock.instant().minusSeconds(60),
            )
        accounts.create(account)
        val factors = InMemoryMfaRepository()
        val cipher = MfaSecretCipher(ByteArray(32) { 7 })
        val enrollmentClock = Clock.fixed(clock.instant().minusSeconds(30), ZoneOffset.UTC)
        val enrolling = MfaService(accounts, factors, cipher, enrollmentClock)
        val enrollment = enrolling.beginEnrollment(account, password)
        enrolling.confirmEnrollment(
            account,
            enrollment.enrollmentId,
            Totp.code(Totp.decodeSecret(enrollment.secret), enrollmentClock.instant().epochSecond),
        )
        val mfa = MfaService(accounts, factors, cipher, clock)
        val draft =
            OwnerBootstrapSpec(
                "owner",
                "owner@example.test",
                0,
                "owner-op",
                "operator-qa",
                "QA owner enrollment",
                clock.instant().epochSecond + 600,
                "p".repeat(43),
                scope = scope,
            )
        val proof =
            mfa.issueStepUp(
                account,
                password,
                Totp.code(Totp.decodeSecret(enrollment.secret), clock.instant().epochSecond),
                StepUpAction.PRIVILEGED_INVITE_ACCEPT,
                "global-owner",
                draft.operationId,
                draft.digest(),
            )
        return Triple(accounts, mfa, draft.copy(proof = proof.proof))
    }
}
