package org.companerodeescuela.api.invitations

import com.mongodb.kotlin.client.coroutine.MongoClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.bson.Document
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@EnabledIfEnvironmentVariable(named = "V12_TEST_MONGODB_URI", matches = ".+")
@Timeout(120)
class MongoInvitationRepositoryTest {
    @Test fun `independent Mongo clients accept only once and never store raw token`(): Unit =
        runBlocking {
            val uri = System.getenv("V12_TEST_MONGODB_URI")
            require(uri == "mongodb://127.0.0.1:27017")
            MongoClient.create(uri).use { client ->
                val db = client.getDatabase("invite_test_" + UUID.randomUUID().toString().replace("-", ""))
                try {
                    val now = Instant.parse("2026-11-03T00:00:00Z")
                    val token = InvitationToken.generate()
                    val first = MongoInvitationRepository(db)
                    assertTrue(
                        first.create(
                            InvitationRecord(
                                "invite",
                                "a@example.test",
                                "campus",
                                InvitationProfile.TEACHER,
                                "issuer",
                                now,
                                now.plusSeconds(600),
                                InvitationStatus.SENT,
                                token.hash,
                            ),
                        ),
                    )
                    val doc = db.getCollection<Document>("platform_invitations").find().first()
                    assertFalse(doc.toJson().contains(token.value))
                    assertFalse(
                        first.create(
                            InvitationRecord(
                                "duplicate",
                                "b@example.test",
                                "campus",
                                InvitationProfile.STUDENT,
                                "issuer",
                                now,
                                now.plusSeconds(600),
                                InvitationStatus.SENT,
                                token.hash,
                            ),
                        ),
                    )
                    val outcomes =
                        coroutineScope {
                            (1..20)
                                .map { n ->
                                    async {
                                        MongoInvitationRepository(
                                            db,
                                        ).accept("invite", token.hash, "a@example.test", true, "account-$n", "op-$n", now)
                                    }
                                }.awaitAll()
                        }
                    assertEquals(1, outcomes.count { it != null })
                    val winner = assertNotNull(outcomes.firstOrNull { it != null })
                    assertNotNull(
                        first.accept("invite", token.hash, "a@example.test", true, winner.acceptedBy!!, winner.acceptedOperation!!, now),
                    )
                    assertNotNull(
                        first.accept(
                            "invite",
                            token.hash,
                            "a@example.test",
                            true,
                            winner.acceptedBy!!,
                            winner.acceptedOperation!!,
                            now.plusSeconds(601),
                        ),
                    )
                    assertNull(first.accept("invite", token.hash, "a@example.test", true, "other", "other", now))
                    assertTrue(first.revoke("invite", now))
                    assertNull(
                        first.accept("invite", token.hash, "a@example.test", true, winner.acceptedBy!!, winner.acceptedOperation!!, now),
                    )
                    val sharedHash = InvitationToken.generate().hash
                    val concurrentCreates =
                        coroutineScope {
                            (1..12)
                                .map { n ->
                                    async {
                                        MongoInvitationRepository(db).create(
                                            InvitationRecord(
                                                "new-$n",
                                                "b@example.test",
                                                "campus",
                                                InvitationProfile.STUDENT,
                                                "issuer",
                                                now,
                                                now.plusSeconds(60),
                                                InvitationStatus.SENT,
                                                sharedHash,
                                            ),
                                        )
                                    }
                                }.awaitAll()
                        }
                    assertEquals(1, concurrentCreates.count { it })
                    val preciseHash = InvitationToken.generate().hash
                    val precise =
                        InvitationRecord(
                            "precision",
                            "c@example.test",
                            "campus",
                            InvitationProfile.STUDENT,
                            "issuer",
                            now.plusNanos(900_000),
                            now.plusNanos(2_900_000),
                            InvitationStatus.SENT,
                            preciseHash,
                        )
                    val memory = InMemoryInvitationRepository()
                    assertTrue(first.create(precise))
                    assertTrue(memory.create(precise))
                    val beforeExpiry = now.plusNanos(1_999_999)
                    val mongoResult = first.accept("precision", preciseHash, "c@example.test", true, "recipient", "operation", beforeExpiry)
                    val memoryResult =
                        memory.accept(
                            "precision",
                            preciseHash,
                            "c@example.test",
                            true,
                            "recipient",
                            "operation",
                            beforeExpiry,
                        )
                    assertEquals(memoryResult?.status, mongoResult?.status)
                    assertNotNull(mongoResult)
                } finally {
                    db.drop()
                }
            }
        }
}
