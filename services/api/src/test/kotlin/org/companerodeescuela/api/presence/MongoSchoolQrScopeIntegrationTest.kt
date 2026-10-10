package org.companerodeescuela.api.presence

import com.mongodb.kotlin.client.coroutine.*
import com.mongodb.client.model.Filters.eq
import java.util.UUID
import kotlinx.coroutines.*
import org.bson.Document
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.companerodeescuela.shared.contracts.*
import kotlin.test.*

@EnabledIfEnvironmentVariable(named="V12_TEST_MONGODB_URI",matches=".+")
@Timeout(120)
class MongoSchoolQrScopeIntegrationTest {
    private fun value()=StoredSchoolEntryQr(SchoolEntryQrResponse("qr","Entrance",status=SchoolEntryQrStatus.ACTIVE,validFromEpochSeconds=1,expiresAtEpochSeconds=10000,createdBy="operator",createdAtEpochSeconds=1,institutionId="campus",operationId="create",commandDigest="a".repeat(64),updatedBy="operator",audit=listOf(SchoolEntryQrAuditEntry("operator","create","a".repeat(64),0,SchoolEntryQrStatus.ACTIVE,1))),"hash")
    @Test fun `parallel Mongo exact scoped creation does not overwrite the first token hash`()=database { db ->
        val results=coroutineScope { (1..24).map { async { MongoSchoolEntryQrRepository(db).insertScoped(value().copy(tokenHash="hash-$it")) } }.awaitAll() }
        assertTrue(results.all { it!=null });assertEquals(1,results.map { it!!.tokenHash }.toSet().size)
        val stored=assertNotNull(MongoSchoolEntryQrRepository(db).findById("qr"));assertEquals(results.first(),stored)
        assertNull(MongoSchoolEntryQrRepository(db).insertScoped(value().copy(response=value().response.copy(commandDigest="b".repeat(64)))))
    }
    @Test fun `Mongo revision CAS prevents a stale usage write from reviving revoked QR`()=database { db ->
        val repo=MongoSchoolEntryQrRepository(db);val initial=assertNotNull(repo.insertScoped(value()))
        val revoked=initial.copy(response=initial.response.copy(status=SchoolEntryQrStatus.REVOKED,revision=1,operationId="revoke",commandDigest="b".repeat(64)))
        assertEquals(revoked,repo.replaceScoped(revoked,0))
        val stale=initial.copy(response=initial.response.copy(usageCount=1,revision=1))
        assertNull(MongoSchoolEntryQrRepository(db).replaceScoped(stale,0))
        assertEquals(revoked,repo.findById("qr"))
    }
    @Test fun `Mongo missing or fractional scope revisions never become zero by numeric coercion`()=database { db ->
        val repo=MongoSchoolEntryQrRepository(db);assertNotNull(repo.insertScoped(value()))
        db.getCollection<Document>("school_entry_qrs").updateOne(eq("_id","qr"),Document("$"+"set",Document("revision",0.5)))
        val malformed=assertNotNull(repo.findById("qr"));assertEquals(-1,malformed.response.revision)
        assertNull(repo.replaceScoped(value().copy(response=value().response.copy(revision=1)),0))
    }
    private fun database(block:suspend(MongoDatabase)->Unit)=runBlocking<Unit> {
        val uri=System.getenv("V12_TEST_MONGODB_URI");require(uri=="mongodb://127.0.0.1:27017")
        MongoClient.create(uri).use { client -> val db=client.getDatabase("v12_test_"+UUID.randomUUID().toString().replace("-",""));try { block(db) } finally { db.drop() } }
    }
}
