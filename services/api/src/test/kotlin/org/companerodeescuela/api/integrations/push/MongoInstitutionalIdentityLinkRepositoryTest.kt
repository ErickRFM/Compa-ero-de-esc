package org.companerodeescuela.api.integrations.push

import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.companerodeescuela.shared.contracts.InstitutionalIdentityKind
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

@EnabledIfEnvironmentVariable(named = "V12_TEST_MONGODB_URI", matches = ".+")
@Timeout(120)
class MongoInstitutionalIdentityLinkRepositoryTest {
    @Test
    fun `Mongo identity links are idempotent tenant scoped and unique per native account`() =
        database { database ->
            val link =
                InstitutionalIdentityLink(
                    institutionId = "campus-a",
                    entityType = InstitutionalIdentityKind.STUDENT,
                    externalId = "SIS-091",
                    nativeUserId = "student-1",
                    linkedBy = "admin-1",
                    operationId = "link-091",
                    payloadDigest = "a".repeat(64),
                    linkedAt = Instant.parse("2026-10-15T12:00:00Z"),
                )
            val outcomes =
                coroutineScope {
                    (1..16)
                        .map { async { MongoInstitutionalIdentityLinkRepository(database).createIfAbsent(link) } }
                        .awaitAll()
                }
            assertEquals(1, outcomes.count { it == InstitutionalIdentityLinkAcceptance.CREATED })
            assertEquals(15, outcomes.count { it == InstitutionalIdentityLinkAcceptance.DUPLICATE })

            val repository = MongoInstitutionalIdentityLinkRepository(database)
            val found = repository.findByExternalId("campus-a", InstitutionalIdentityKind.STUDENT, "SIS-091")
            val otherTenant = repository.findByExternalId("campus-b", InstitutionalIdentityKind.STUDENT, "SIS-091")
            assertEquals(link, found)
            assertEquals(null, otherTenant)
            assertEquals(
                InstitutionalIdentityLinkAcceptance.CONFLICT,
                repository.createIfAbsent(link.copy(nativeUserId = "student-2")),
            )
            assertEquals(
                InstitutionalIdentityLinkAcceptance.CONFLICT,
                repository.createIfAbsent(link.copy(externalId = "SIS-092", operationId = "link-092")),
            )
        }

    private fun database(block: suspend (MongoDatabase) -> Unit) =
        runBlocking {
            val uri = System.getenv("V12_TEST_MONGODB_URI")
            require(uri == "mongodb://127.0.0.1:27017")
            MongoClient.create(uri).use { client ->
                val database = client.getDatabase("v12_test_${UUID.randomUUID().toString().replace("-", "")}")
                try {
                    block(database)
                } finally {
                    database.drop()
                }
            }
        }
}
