package org.companerodeescuela.api.tutoring

import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.*
import org.bson.Document
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.companerodeescuela.api.errors.ApiException
import kotlin.test.*

@EnabledIfEnvironmentVariable(named="V12_TEST_MONGODB_URI",matches=".+")
@Timeout(120)
class MongoTutorAssignmentIntegrationTest {
    private val now=Instant.parse("2026-10-10T07:00:00Z")
    private fun record()=TutorAssignmentRecord("operation-assignment","tutor","6A",true,"admin",now,institutionId="campus",periodId="period",startsAt=now,endsAt=now.plusSeconds(600),tutorRoleGeneration=2)
    @Test fun `concurrent independent repositories persist one full scoped assignment and exact retries`()=database { db ->
        val record=record()
        val results=coroutineScope { (1..24).map { async { MongoTutorAssignmentRepository(db).create(record) } }.awaitAll() }
        assertTrue(results.all { it==record })
        val restored=MongoTutorAssignmentRepository(db);assertEquals(listOf(record),restored.listAll())
        assertEquals(record,restored.findActive("tutor","6A"))
    }
    @Test fun `Mongo different scope retry and revoked ID cannot overwrite original evidence`()=database { db ->
        val repo=MongoTutorAssignmentRepository(db);val record=record();repo.create(record)
        for(changed in listOf(record.copy(id="different-operation"),record.copy(periodId="other-period"),record.copy(tutorRoleGeneration=3))) assertFailsWith<ApiException.Conflict> { repo.create(changed) }
        val revoked=assertNotNull(repo.revoke(record.id,"admin",now))
        assertFailsWith<ApiException.Conflict> { MongoTutorAssignmentRepository(db).create(record) }
        assertEquals(revoked,repo.findById(record.id))
        val next=record.copy(id="next-operation",tutorRoleGeneration=4)
        assertEquals(next,repo.create(next));assertEquals(2,repo.listAll().size)
    }
    @Test fun `fractional or absent tutor generation cannot become a valid old grant`()=database { db ->
        val repo=MongoTutorAssignmentRepository(db);repo.create(record())
        val collection=db.getCollection<Document>("tutor_assignments")
        collection.updateOne(com.mongodb.client.model.Filters.eq("_id",record().id),com.mongodb.client.model.Updates.set("tutorRoleGeneration",2.5))
        assertEquals(-1,assertNotNull(MongoTutorAssignmentRepository(db).findById(record().id)).tutorRoleGeneration)
        collection.updateOne(com.mongodb.client.model.Filters.eq("_id",record().id),com.mongodb.client.model.Updates.unset("tutorRoleGeneration"))
        assertEquals(-1,assertNotNull(repo.findById(record().id)).tutorRoleGeneration)
    }
    private fun database(block:suspend(MongoDatabase)->Unit)=runBlocking {
        val uri=System.getenv("V12_TEST_MONGODB_URI");require(uri=="mongodb://127.0.0.1:27017")
        MongoClient.create(uri).use { client -> val db=client.getDatabase("v12_test_"+UUID.randomUUID().toString().replace("-",""));try { block(db) } finally { db.drop() } }
    }
}
