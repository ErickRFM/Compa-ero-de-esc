package org.companerodeescuela.api.admin

import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Updates.set
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.bson.Document
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@EnabledIfEnvironmentVariable(named = "V12_TEST_MONGODB_URI", matches = ".+")
@Timeout(120)
class MongoQaAdminBootstrapIntegrationTest {
    @Test
    fun `independent startups reserve one exact institution command`() =
        withDatabase { db ->
            val results =
                coroutineScope {
                    (1..16).map { index -> async { MongoQaAdminBootstrapClaims(db).claim("qa", "digest-$index") } }.awaitAll()
                }
            assertEquals(1, results.count { it })
        }

    @Test
    fun `completed bootstrap cannot reopen subsequently closed QA registration`() =
        withDatabase { db ->
            val institutions = db.getCollection<Document>("platform_institutions")
            institutions.insertOne(Document("_id", "qa").append("displayName", "QA-UNIV").append("registrationEnabled", false))
            val claims = MongoQaAdminBootstrapClaims(db)
            assertFalse(claims.activateRegistration("qa", "digest"))
            assertTrue(claims.claim("qa", "digest"))
            assertTrue(claims.activateRegistration("qa", "digest"))
            assertTrue(MongoQaAdminBootstrapClaims(db).activateRegistration("qa", "digest"))
            institutions.updateOne(eq("_id", "qa"), set("registrationEnabled", false))
            assertFalse(MongoQaAdminBootstrapClaims(db).activateRegistration("qa", "digest"))
            assertFalse(institutions.find(eq("_id", "qa")).first().getBoolean("registrationEnabled"))
        }

    @Test
    fun `reservation never opens an unrelated institution`() =
        withDatabase { db ->
            val institutions = db.getCollection<Document>("platform_institutions")
            institutions.insertOne(Document("_id", "real").append("displayName", "Real institution").append("registrationEnabled", false))
            val claims = MongoQaAdminBootstrapClaims(db)
            assertTrue(claims.claim("real", "digest"))
            assertFalse(claims.activateRegistration("real", "digest"))
            assertFalse(institutions.find(eq("_id", "real")).first().getBoolean("registrationEnabled"))
        }

    private fun withDatabase(block: suspend (MongoDatabase) -> Unit) =
        runBlocking {
            val uri = System.getenv("V12_TEST_MONGODB_URI")
            require(uri == "mongodb://127.0.0.1:27017")
            MongoClient.create(uri).use { client ->
                val database = client.getDatabase("qa_bootstrap_${UUID.randomUUID().toString().replace("-", "")}")
                try {
                    block(database)
                } finally {
                    database.drop()
                }
            }
        }
}
