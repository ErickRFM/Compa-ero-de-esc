package org.companerodeescuela.api.events

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
class MongoAcademicEventScopeIntegrationTest {
    private fun event()=AcademicEvent("event",AcademicEventType.ANNOUNCEMENT,AcademicEventSource.TEACHER,"teacher",target=AcademicEventTarget(AcademicEventScope.COURSE,"class"),title="Notice",createdAtEpochSeconds=1,institutionId="campus",groupScopeRevision=0,revision=1,operationId="create",commandDigest="a".repeat(64),authorRoleGeneration=0,audit=listOf(AcademicEventAuditEntry("teacher","create","a".repeat(64),0,1)))
    @Test fun `parallel Mongo scoped event creation preserves a single canonical audited record`()=database { db ->
        val results=coroutineScope { (1..24).map { async { MongoAcademicEventRepository(db).saveScoped(event(),0) } }.awaitAll() }
        assertTrue(results.all { it==event() });assertEquals(listOf(event()),MongoAcademicEventRepository(db).listEvents())
        assertNull(MongoAcademicEventRepository(db).saveScoped(event().copy(title="different"),0))
    }
    @Test fun `Mongo event cancellation is a retained tombstone and rejects stale competing updates`()=database { db ->
        val repo=MongoAcademicEventRepository(db);assertNotNull(repo.saveScoped(event(),0))
        val cancelled=event().copy(cancelled=true,revision=2,operationId="cancel",commandDigest="b".repeat(64),audit=event().audit+AcademicEventAuditEntry("teacher","cancel","b".repeat(64),1,2,true))
        assertEquals(cancelled,repo.saveScoped(cancelled,1));assertEquals(cancelled,MongoAcademicEventRepository(db).saveScoped(cancelled,1))
        assertNull(repo.saveScoped(event().copy(revision=2,operationId="update",title="changed",commandDigest="c".repeat(64)),1))
        assertEquals(cancelled,repo.findById("event"))
    }
    @Test fun `missing and fractional persisted event revision deny mutation without inferred migration`()=database { db ->
        val repo=MongoAcademicEventRepository(db);assertNotNull(repo.saveScoped(event(),0))
        val collection=db.getCollection<Document>("academic_events")
        collection.updateOne(eq("_id","event"),Document("$"+"set",Document("revision",1.5)))
        assertEquals(-1,repo.findById("event")!!.revision)
        assertNull(repo.saveScoped(event().copy(revision=2),1))
        collection.updateOne(eq("_id","event"),Document("$"+"unset",Document("revision","")))
        assertEquals(-1,repo.findById("event")!!.revision)
    }
    private fun database(block:suspend(MongoDatabase)->Unit)=runBlocking<Unit> {
        val uri=System.getenv("V12_TEST_MONGODB_URI");require(uri=="mongodb://127.0.0.1:27017")
        MongoClient.create(uri).use { client -> val db=client.getDatabase("v12_test_"+UUID.randomUUID().toString().replace("-",""));try { block(db) } finally { db.drop() } }
    }
}
