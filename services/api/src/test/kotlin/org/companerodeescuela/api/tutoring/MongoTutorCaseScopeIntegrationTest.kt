package org.companerodeescuela.api.tutoring

import com.mongodb.kotlin.client.coroutine.*
import com.mongodb.client.model.Filters.eq
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.*
import org.bson.Document
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.companerodeescuela.shared.contracts.*
import kotlin.test.*

@EnabledIfEnvironmentVariable(named="V12_TEST_MONGODB_URI",matches=".+")
@Timeout(120)
class MongoTutorCaseScopeIntegrationTest {
    private fun value()=TutorCaseRecord("case","9A","student","Retained private support",TutorCaseStatus.OPEN,"tutor",Instant.EPOCH,Instant.EPOCH,institutionId="campus",groupScopeRevision=0)
    @Test fun `Mongo tutor case persists exact native institution and scope without allowing tenant mutation`()=database { db ->
        val repo=MongoTutorCaseRepository(db);assertEquals(value(),repo.create(value()));assertEquals(value(),repo.find("case"))
        assertNull(repo.compareAndUpdate(value(),value().copy(version=1,institutionId="other")))
        assertEquals(value(),repo.find("case"))
    }
    @Test fun `Mongo missing or fractional case metadata denies update rather than coercing scope revision`()=database { db ->
        val repo=MongoTutorCaseRepository(db);repo.create(value())
        val collection=db.getCollection<Document>("tutoring_cases")
        collection.updateOne(eq("_id","case"),Document("$"+"set",Document("groupScopeRevision",0.5)))
        val unknown=assertNotNull(repo.find("case"));assertEquals(-1,unknown.groupScopeRevision)
        assertNull(repo.compareAndUpdate(unknown,unknown.copy(version=1)))
        collection.updateOne(eq("_id","case"),Document("$"+"unset",Document("groupScopeRevision","")))
        assertEquals(-1,repo.find("case")!!.groupScopeRevision)
    }
    @Test fun `independent Mongo case note revisions admit one writer and retain original scoped evidence`()=database { db ->
        val repo=MongoTutorCaseRepository(db);repo.create(value())
        val results=coroutineScope { (1..24).map { async {
            val note=TutorCaseNoteRecord("note-$it","Private note $it",TutorNoteVisibility.TUTOR_INTERNAL,"tutor",Instant.EPOCH)
            MongoTutorCaseRepository(db).compareAndUpdate(value(),value().copy(version=1,notes=listOf(note)))
        } }.awaitAll() }
        assertEquals(1,results.count { it!=null });val stored=assertNotNull(repo.find("case"))
        assertEquals("campus",stored.institutionId);assertEquals(0,stored.groupScopeRevision);assertEquals(1,stored.notes.size)
    }
    private fun database(block:suspend(MongoDatabase)->Unit)=runBlocking<Unit> {
        val uri=System.getenv("V12_TEST_MONGODB_URI");require(uri=="mongodb://127.0.0.1:27017")
        MongoClient.create(uri).use { client -> val db=client.getDatabase("v12_test_"+UUID.randomUUID().toString().replace("-",""));try { block(db) } finally { db.drop() } }
    }
}
