package org.companerodeescuela.api.auth

import com.mongodb.kotlin.client.coroutine.MongoClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.bson.Document
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

@EnabledIfEnvironmentVariable(named = "V12_TEST_MONGODB_URI", matches = ".+")
@Timeout(120)
class MongoOwnerInvitationRepositoryTest {
    @Test
    fun `independent Mongo repositories atomically consume a digest once without storing the raw code`() =
        runBlocking {
            val uri = System.getenv("V12_TEST_MONGODB_URI")
            require(uri == "mongodb://127.0.0.1:27017")
            MongoClient.create(uri).use { client ->
                val database = client.getDatabase("owner_invite_" + UUID.randomUUID().toString().replace("-", ""))
                try {
                    val now = Instant.parse("2026-11-07T12:00:00Z")
                    val expiresAt = now.plusSeconds(3600)
                    val rawCode = "x".repeat(43)
                    val digest =
                        MessageDigest
                            .getInstance("SHA-256")
                            .digest(rawCode.toByteArray(Charsets.UTF_8))
                            .joinToString("") { "%02x".format(it) }
                    val outcomes =
                        coroutineScope {
                            (1..24)
                                .map { index ->
                                    async {
                                        MongoOwnerInvitationRepository(database).consume(
                                            digest,
                                            "erik55509@hotmail.com",
                                            expiresAt,
                                            now,
                                            "account-$index",
                                        )
                                    }
                                }.awaitAll()
                        }

                    assertEquals(1, outcomes.count { it })
                    val document =
                        assertNotNull(
                            database
                                .getCollection<Document>("owner_registration_invitations")
                                .find()
                                .first(),
                        )
                    assertEquals(digest, document.getString("_id"))
                    assertFalse(document.toJson().contains(rawCode))
                    assertNotNull(document.getDate("consumedAt"))
                    assertFalse(
                        MongoOwnerInvitationRepository(database).consume(
                            digest,
                            "erik55509@hotmail.com",
                            expiresAt,
                            now,
                            "replay",
                        ),
                    )
                } finally {
                    database.drop()
                }
            }
        }
}
