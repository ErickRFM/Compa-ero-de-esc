package org.companerodeescuela.api.admin

import com.mongodb.kotlin.client.coroutine.MongoClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.companerodeescuela.api.auth.MongoPlatformAccountRepository
import org.companerodeescuela.api.auth.PasswordHasher
import org.companerodeescuela.api.auth.PlatformAccount
import org.companerodeescuela.api.security.MfaSecretCipher
import org.companerodeescuela.api.security.MfaService
import org.companerodeescuela.api.security.MongoMfaRepository
import org.companerodeescuela.api.security.Totp
import org.companerodeescuela.shared.contracts.StepUpAction
import org.companerodeescuela.shared.contracts.UserRole
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@EnabledIfEnvironmentVariable(named = "V12_TEST_MONGODB_URI", matches = ".+")
@Timeout(120)
class MongoOwnerBootstrapClaimsTest {
    @Test fun `independent Mongo services promote only once and preserve the student identity`() =
        runBlocking {
            val uri = System.getenv("V12_TEST_MONGODB_URI")
            require(uri == "mongodb://127.0.0.1:27017")
            MongoClient.create(uri).use { client ->
                val databaseName = "owner_" + UUID.randomUUID().toString().replace("-", "") + "_qa"
                val db = client.getDatabase(databaseName)
                try {
                    val accounts = MongoPlatformAccountRepository(db)
                    val password = "owner-test-password"
                    val now = Instant.parse("2026-11-02T12:00:30Z")
                    val clock = Clock.fixed(now, ZoneOffset.UTC)
                    val account =
                        PlatformAccount(
                            "owner",
                            "Owner",
                            "owner@example.test",
                            PasswordHasher().hash(password),
                            setOf(UserRole.STUDENT),
                            institutionId = "campus",
                            emailVerifiedAt = now.minusSeconds(30),
                        )
                    assertTrue(accounts.create(account))
                    val key = MfaSecretCipher(ByteArray(32) { 5 })
                    val earlier = Clock.fixed(now.minusSeconds(30), ZoneOffset.UTC)
                    val enrolling = MfaService(accounts, MongoMfaRepository(db), key, earlier)
                    val enrollment = enrolling.beginEnrollment(account, password)
                    enrolling.confirmEnrollment(
                        account,
                        enrollment.enrollmentId,
                        Totp.code(Totp.decodeSecret(enrollment.secret), earlier.instant().epochSecond),
                    )
                    val mfa = MfaService(accounts, MongoMfaRepository(db), key, clock)
                    val draft =
                        OwnerBootstrapSpec(
                            "owner",
                            "owner@example.test",
                            0,
                            "operation",
                            "qa-operator",
                            "QA owner test",
                            now.epochSecond + 600,
                            "p".repeat(43),
                        )
                    val proof =
                        mfa.issueStepUp(
                            account,
                            password,
                            Totp.code(Totp.decodeSecret(enrollment.secret), now.epochSecond),
                            StepUpAction.PRIVILEGED_INVITE_ACCEPT,
                            "global-owner",
                            draft.operationId,
                            draft.digest(),
                        )
                    val spec = draft.copy(proof = proof.proof)
                    val outcomes =
                        coroutineScope {
                            (1..12)
                                .map {
                                    async {
                                        runCatching {
                                            val repository = MongoPlatformAccountRepository(db)
                                            OwnerBootstrap.provision(
                                                spec,
                                                "staging",
                                                databaseName,
                                                repository,
                                                MfaService(repository, MongoMfaRepository(db), key, clock),
                                                MongoOwnerBootstrapClaims(db),
                                                clock,
                                            )
                                        }.isSuccess
                                    }
                                }.awaitAll()
                        }
                    assertTrue(outcomes.any { it })
                    val updated = assertNotNull(accounts.findById("owner"))
                    assertEquals(setOf(UserRole.STUDENT, UserRole.SUPER_ADMIN), updated.roles)
                    assertEquals("campus", updated.institutionId)
                    assertEquals(account.passwordHash, updated.passwordHash)
                    assertEquals(1L, updated.authRevision)
                    assertEquals(1, updated.identityAudit.size)
                } finally {
                    db.drop()
                }
            }
        }

    @Test fun `expired reservation is reclaimed by one concurrent recovery and committed claims remain permanent`() =
        runBlocking {
            val uri = System.getenv("V12_TEST_MONGODB_URI")
            require(uri == "mongodb://127.0.0.1:27017")
            MongoClient.create(uri).use { client ->
                val db = client.getDatabase("owner_recovery_" + UUID.randomUUID().toString().replace("-", ""))
                try {
                    val authorizedAt = Instant.parse("2026-11-07T12:00:00Z")
                    val stale =
                        OwnerBootstrapClaim(
                            "a".repeat(64),
                            "candidate-old",
                            "operator-old",
                            "interrupted bootstrap",
                            authorizedAt,
                            authorizedAt.plusSeconds(900),
                        )
                    assertTrue(MongoOwnerBootstrapClaims(db).reserve(stale))

                    val recoveryAt = authorizedAt.plusSeconds(901)
                    val outcomes =
                        coroutineScope {
                            (1..24)
                                .map { index ->
                                    async {
                                        MongoOwnerBootstrapClaims(db).reserve(
                                            OwnerBootstrapClaim(
                                                "%064x".format(index),
                                                "candidate-$index",
                                                "operator-$index",
                                                "fresh signed recovery",
                                                recoveryAt,
                                                recoveryAt.plusSeconds(900),
                                            ),
                                        )
                                    }
                                }.awaitAll()
                        }
                    assertEquals(1, outcomes.count { it })

                    val winner = assertNotNull(MongoOwnerBootstrapClaims(db).find())
                    assertEquals(OwnerBootstrapClaimState.RESERVED, winner.state)
                    assertTrue(
                        MongoOwnerBootstrapClaims(db).commit(
                            winner.digest,
                            winner.accountId,
                            recoveryAt.plusSeconds(30),
                        ),
                    )
                    val later = recoveryAt.plusSeconds(1800)
                    assertFalse(
                        MongoOwnerBootstrapClaims(db).reserve(
                            OwnerBootstrapClaim(
                                "f".repeat(64),
                                "candidate-reassigned",
                                "operator-reassigned",
                                "must not replace committed owner",
                                later,
                                later.plusSeconds(900),
                            ),
                        ),
                    )
                    assertEquals(OwnerBootstrapClaimState.COMMITTED, MongoOwnerBootstrapClaims(db).find()?.state)
                    assertEquals(winner.digest, MongoOwnerBootstrapClaims(db).find()?.digest)
                } finally {
                    db.drop()
                }
            }
        }

    @Test fun `concurrent independent operators reserve only one owner command`() =
        runBlocking {
            val uri = System.getenv("V12_TEST_MONGODB_URI")
            require(uri == "mongodb://127.0.0.1:27017")
            MongoClient.create(uri).use { client ->
                val db = client.getDatabase("owner_test_" + UUID.randomUUID().toString().replace("-", ""))
                try {
                    val outcomes =
                        coroutineScope {
                            (1..20)
                                .map { n ->
                                    async {
                                        MongoOwnerBootstrapClaims(
                                            db,
                                        ).reserve(
                                            OwnerBootstrapClaim(
                                                "%064x".format(n),
                                                "account-$n",
                                                "operator-$n",
                                                "QA test",
                                                Instant.now(),
                                                Instant.now().plusSeconds(900),
                                            ),
                                        )
                                    }
                                }.awaitAll()
                        }
                    assertEquals(1, outcomes.count { it })
                    val winner = MongoOwnerBootstrapClaims(db).find()
                    assertTrue(winner != null && winner.digest.matches(Regex("[a-f0-9]{64}")))
                    assertTrue(MongoOwnerBootstrapClaims(db).reserve(winner))
                    assertTrue(
                        MongoOwnerBootstrapClaims(db).commit(
                            winner.digest,
                            winner.accountId,
                            Instant.now(),
                        ),
                    )
                    assertFalse(
                        MongoOwnerBootstrapClaims(db).reserve(
                            OwnerBootstrapClaim(
                                "f".repeat(64),
                                "replacement",
                                "new-operator",
                                "must not replace committed claim",
                                Instant.now().plusSeconds(3600),
                                Instant.now().plusSeconds(4500),
                            ),
                        ),
                    )
                } finally {
                    db.drop()
                }
            }
        }
}
