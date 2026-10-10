package org.companerodeescuela.api.attendance

import com.mongodb.kotlin.client.coroutine.*
import com.mongodb.client.model.Filters.eq
import org.bson.Document
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.companerodeescuela.shared.contracts.*
import kotlin.test.*

@EnabledIfEnvironmentVariable(named="V12_TEST_MONGODB_URI",matches=".+")
@Timeout(120)
class MongoAttendanceSourceIntegrationTest {
    private fun value(id:String="native")=AttendanceSessionResponse(id,"native-meeting","native-class","group","2026-10-05","07:00","07:50","teacher",1,1000,status=AttendanceSessionStatus.OPEN,identitySource=AttendanceIdentitySource.NATIVE,institutionId="campus",groupId="group-id",groupScopeRevision=4)
    @Test fun `independent Mongo repositories preserve native attendance source and scope through one canonical meeting`()=database { db ->
        val results=coroutineScope { (1..24).map { async { MongoAttendanceRepository(db).createSession(value("session-$it")) } }.awaitAll() }
        val ids=results.map { when(it) { is SessionWriteResult.Created -> it.session.id;is SessionWriteResult.Existing -> it.session.id } }
        assertEquals(1,ids.toSet().size)
        val actual=assertNotNull(MongoAttendanceRepository(db).findSession(ids.first()))
        assertEquals(value(actual.id),actual)
    }
    @Test fun `missing and fractional native attendance revision never become a current group scope`()=database { db ->
        val repo=MongoAttendanceRepository(db);repo.createSession(value())
        db.getCollection<Document>("attendance_sessions").updateOne(eq("_id","native"),Document("$"+"set",Document("groupScopeRevision",4.5)))
        assertEquals(-1,repo.findSession("native")!!.groupScopeRevision)
        db.getCollection<Document>("attendance_sessions").updateOne(eq("_id","native"),Document("$"+"unset",Document("groupScopeRevision",1)))
        assertEquals(-1,repo.findSession("native")!!.groupScopeRevision)
    }
    @Test fun `native session closure preserves actual source metadata rather than relabeling old attendance`()=database { db ->
        val repo=MongoAttendanceRepository(db);repo.createSession(value())
        val closed=value().copy(status=AttendanceSessionStatus.CLOSED,closedAtEpochSeconds=20)
        repo.replaceSession(closed)
        assertEquals(closed,MongoAttendanceRepository(db).findSession(closed.id))
    }
    private fun database(block:suspend(MongoDatabase)->Unit)=runBlocking<Unit> {
        val uri=System.getenv("V12_TEST_MONGODB_URI");require(uri=="mongodb://127.0.0.1:27017")
        MongoClient.create(uri).use { client -> val db=client.getDatabase("v12_test_"+UUID.randomUUID().toString().replace("-",""));try { block(db) } finally { db.drop() } }
    }
}
