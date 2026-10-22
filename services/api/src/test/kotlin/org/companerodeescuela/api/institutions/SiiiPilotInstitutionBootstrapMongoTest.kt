package org.companerodeescuela.api.institutions

import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@EnabledIfEnvironmentVariable(named = "V12_TEST_MONGODB_URI", matches = ".+")
@Timeout(120)
class SiiiPilotInstitutionBootstrapMongoTest {
    @Test
    fun `bootstrap creates one private tenant and never changes existing metadata`() =
        database { db ->
            val desired = PilotInstitutionSpec("pilot-1", "Piloto SIII")
            SiiiPilotInstitutionBootstrap.provision(db, desired)
            SiiiPilotInstitutionBootstrap.provision(db, desired)
            val institutions = MongoInstitutionRepository(db)
            assertEquals(desired.tenantId, institutions.findConfigured(desired.tenantId)?.id)
            assertEquals(null, institutions.findEnabled(desired.tenantId))
            assertFailsWith<IllegalArgumentException> {
                SiiiPilotInstitutionBootstrap.provision(db, desired.copy(displayName = "Different"))
            }
            assertEquals("Piloto SIII", institutions.findConfigured(desired.tenantId)?.displayName)
        }

    private fun database(block: suspend (MongoDatabase) -> Unit) =
        runBlocking {
            val uri = System.getenv("V12_TEST_MONGODB_URI")
            require(uri == "mongodb://127.0.0.1:27017")
            MongoClient.create(uri).use { client ->
                val database = client.getDatabase("pilot_test_" + UUID.randomUUID().toString().replace("-", ""))
                try {
                    block(database)
                } finally {
                    database.drop()
                }
            }
        }
}
