package org.companerodeescuela.api.classroom

import com.mongodb.kotlin.client.coroutine.*
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Updates.*
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.*
import org.bson.Document
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.*
import kotlin.test.*

@EnabledIfEnvironmentVariable(named="V12_TEST_MONGODB_URI",matches=".+")
@Timeout(120)
class MongoClassroomScopeIntegrationTest {
    private val now=Instant.parse("2026-10-10T07:00:00Z")
    private fun classroom()=NativeClassroom("operation-class","QA Class",null,"Room","6A","Actual Group","teacher","Actual Teacher",ClassroomStatus.ACTIVE,now,0,2,"admin","assign","a".repeat(64))
    @Test fun `independent operators persist one full scoped teacher assignment and exact retries`()=database { db ->
        val current=classroom()
        coroutineScope { (1..24).map { async { MongoClassroomRepository(db).createClassroom(current) } }.awaitAll() }
        val repo=MongoClassroomRepository(db)
        assertEquals(listOf(current),repo.listClassrooms());assertEquals(current,repo.findClassroom(current.id))
        assertFailsWith<ApiException.Conflict> { repo.createClassroom(current.copy(teacherId="another")) }
        assertFailsWith<ApiException.Conflict> { repo.createClassroom(current.copy(groupScopeRevision=1)) }
    }
    @Test fun `missing or fractional assignment generations cannot become a valid native class`()=database { db ->
        val repo=MongoClassroomRepository(db);val current=classroom();repo.createClassroom(current)
        val collection=db.getCollection<Document>("native_classrooms")
        collection.updateOne(eq("_id",current.id),set("teacherRoleGeneration",2.5))
        assertEquals(-1,assertNotNull(repo.findClassroom(current.id)).teacherRoleGeneration)
        collection.updateOne(eq("_id",current.id),unset("groupScopeRevision"))
        assertEquals(-1,assertNotNull(repo.findClassroom(current.id)).groupScopeRevision)
    }
    @Test fun `actual Mongo membership is unique and invitation revocation remains enforced`()=database { db ->
        val repo=MongoClassroomRepository(db);val current=classroom();repo.createClassroom(current)
        val membership=NativeClassroomMembership(current.id,"student",ClassroomMemberRole.STUDENT,now)
        val results=coroutineScope { (1..24).map { async { MongoClassroomRepository(db).addMembershipIfAbsent(membership) } }.awaitAll() }
        assertTrue(results.all { it==membership });assertEquals(1,repo.listClassroomsForUser("student").size)
        assertEquals(listOf(membership),repo.listMembershipsForClass(current.id));assertTrue(repo.listMembershipsForClass("other").isEmpty())
        val invite=NativeClassInviteRecord("invite",current.id,"test-only-hash","admin",now,now.plusSeconds(600),1)
        repo.createInvite(invite);assertTrue(repo.revokeInvite(invite.id,current.id,now))
        assertNull(MongoClassroomRepository(db).consumeInvite(invite.id,now.plusSeconds(1)))
    }
    private fun database(block:suspend(MongoDatabase)->Unit)=runBlocking<Unit> {
        val uri=System.getenv("V12_TEST_MONGODB_URI");require(uri=="mongodb://127.0.0.1:27017")
        MongoClient.create(uri).use { client -> val db=client.getDatabase("v12_test_"+UUID.randomUUID().toString().replace("-",""));try { block(db) } finally { db.drop() } }
    }
}
