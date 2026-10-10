package org.companerodeescuela.api.academic.groups

import com.mongodb.kotlin.client.coroutine.*
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Updates.*
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.*
import org.bson.Document
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.*

@EnabledIfEnvironmentVariable(named="V12_TEST_MONGODB_URI",matches=".+")
@Timeout(120)
class MongoAcademicGroupScopeIntegrationTest {
    private val now=Instant.parse("2026-10-10T07:00:00Z")
    private fun change()=AcademicGroupScopeChange("operation","owner","campus","period",now,now.plusSeconds(3600),now,"a".repeat(64))
    @Test fun `independent Mongo operators migrate legacy scope once and retain exact audit`()=database { db ->
        val repo=MongoAcademicGroupRepository(db);repo.create(AcademicGroupRecord("6A","6A",true,now))
        val results=coroutineScope { (1..24).map { async { MongoAcademicGroupRepository(db).changeScope("6A",0,change()) } }.awaitAll() }
        assertTrue(results.all { it!=null })
        val current=assertNotNull(MongoAcademicGroupRepository(db).find("6A"));assertEquals(1,current.scopeRevision);assertEquals(1,current.scopeAudit.size)
        assertEquals("campus",current.institutionId);assertEquals(change().payloadDigest,current.scopeAudit.single().payloadDigest)
        assertNull(repo.changeScope("6A",0,change().copy(actorId="another")))
        assertNull(repo.changeScope("6A",0,change().copy(payloadDigest="b".repeat(64))))
        assertNull(repo.changeScope("6A",1,change().copy(operationId="move",institutionId="other")))
    }
    @Test fun `Mongo retains increasing revisions when period labels return`()=database { db ->
        val repo=MongoAcademicGroupRepository(db);repo.create(AcademicGroupRecord("6A","6A",true,now))
        assertNotNull(repo.changeScope("6A",0,change()))
        assertNotNull(repo.changeScope("6A",1,change().copy(operationId="next",periodId="next",payloadDigest="b".repeat(64))))
        assertNotNull(repo.changeScope("6A",2,change().copy(operationId="restore",payloadDigest="c".repeat(64))))
        val restored=assertNotNull(MongoAcademicGroupRepository(db).find("6A"))
        assertEquals(3,restored.scopeRevision);assertEquals("period",restored.periodId);assertEquals(3,restored.scopeAudit.size)
    }
    @Test fun `missing legacy revision migrates explicitly while fractional revision is rejected`()=database { db ->
        val repo=MongoAcademicGroupRepository(db);repo.create(AcademicGroupRecord("6A","6A",true,now))
        val collection=db.getCollection<Document>("academic_groups")
        collection.updateOne(eq("_id","6A"),unset("scopeRevision"))
        assertNotNull(repo.changeScope("6A",0,change()))
        collection.updateOne(eq("_id","6A"),set("scopeRevision",1.5))
        assertEquals(-1,assertNotNull(repo.find("6A")).scopeRevision)
        assertNull(repo.changeScope("6A",1,change().copy(operationId="tampered")))
    }
    private fun database(block:suspend(MongoDatabase)->Unit)=runBlocking<Unit> {
        val uri=System.getenv("V12_TEST_MONGODB_URI");require(uri=="mongodb://127.0.0.1:27017")
        MongoClient.create(uri).use { client -> val db=client.getDatabase("v12_test_"+UUID.randomUUID().toString().replace("-",""));try { block(db) } finally { db.drop() } }
    }
}
