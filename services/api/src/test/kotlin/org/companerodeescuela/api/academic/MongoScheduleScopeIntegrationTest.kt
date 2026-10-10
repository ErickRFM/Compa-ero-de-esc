package org.companerodeescuela.api.academic

import com.mongodb.kotlin.client.coroutine.*
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.companerodeescuela.shared.contracts.*
import kotlin.test.*

@EnabledIfEnvironmentVariable(named="V12_TEST_MONGODB_URI",matches=".+")
@Timeout(120)
class MongoScheduleScopeIntegrationTest {
    private fun block()=ManagedScheduleBlock("teacher",ScheduleBlock("operation","MONDAY","07:00","07:50",subjectName="QA Class",teacherId="teacher",groupId="6A",provenance=AcademicProvenance(AcademicDataSource.ADMIN_MANUAL,createdBy="admin",updatedBy="admin",createdAtEpochSeconds=1,updatedAtEpochSeconds=1)),institutionId="campus",groupScopeRevision=0,revision=1,operationId="operation",operationDigest="a".repeat(64))
    @Test fun `Mongo parallel exact scoped creations persist one full audit revision`()=database { db ->
        val results=coroutineScope { (1..24).map { async { MongoAcademicScheduleOverrideRepository(db).saveScoped(block(),0) } }.awaitAll() }
        assertTrue(results.all { it==block() });assertEquals(listOf(block()),MongoAcademicScheduleOverrideRepository(db).listForOwner("teacher"))
    }
    @Test fun `Mongo rejects stale competing schedule change and altered operation digest`()=database { db ->
        val repo=MongoAcademicScheduleOverrideRepository(db);assertNotNull(repo.saveScoped(block(),0))
        assertNull(repo.saveScoped(block().copy(operationDigest="b".repeat(64)),0))
        val changed=block().copy(revision=2,operationId="next",operationDigest="b".repeat(64),block=block().block.copy(room="Room"))
        assertNotNull(repo.saveScoped(changed,1))
        assertNull(MongoAcademicScheduleOverrideRepository(db).saveScoped(block(),0))
        assertEquals(changed,repo.findById("operation"))
    }
    @Test fun `cancellation remains a retained scoped tombstone with exact retry`()=database { db ->
        val repo=MongoAcademicScheduleOverrideRepository(db);assertNotNull(repo.saveScoped(block(),0))
        val cancelled=block().copy(revision=2,operationId="cancel",operationDigest="c".repeat(64),block=block().block.copy(status=BlockStatus.CANCELLED))
        assertEquals(cancelled,repo.saveScoped(cancelled,1))
        assertEquals(cancelled,MongoAcademicScheduleOverrideRepository(db).saveScoped(cancelled,1))
        assertEquals(1,repo.listForOwner("teacher").size)
    }
    private fun database(block:suspend(MongoDatabase)->Unit)=runBlocking<Unit> {
        val uri=System.getenv("V12_TEST_MONGODB_URI");require(uri=="mongodb://127.0.0.1:27017")
        MongoClient.create(uri).use { client -> val db=client.getDatabase("v12_test_"+UUID.randomUUID().toString().replace("-",""));try { block(db) } finally { db.drop() } }
    }
}
