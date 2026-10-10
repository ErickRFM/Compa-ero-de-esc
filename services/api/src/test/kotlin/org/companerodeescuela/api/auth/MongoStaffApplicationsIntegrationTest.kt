package org.companerodeescuela.api.auth

import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
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
class MongoStaffApplicationsIntegrationTest {
    private val now=Instant.parse("2026-10-10T07:00:00Z")
    private fun request(id:String="request")=StaffApplication(id,StaffRole.TUTOR,"campus","reference",null,now.epochSecond)
    private suspend fun seed(repo:PlatformAccountRepository) { assertTrue(repo.create(PlatformAccount("teacher","QA","teacher@example.test","test-only-hash",setOf(UserRole.TEACHER),institutionId="campus",emailVerifiedAt=now))) }
    @Test fun `independent repositories create only one pending application without granting a role`()=database { db ->
        val repo=MongoPlatformAccountRepository(db);seed(repo)
        val results=coroutineScope { (1..24).map { n -> async { MongoPlatformAccountRepository(db).submitStaffApplication("teacher",0,request("request-$n")) } }.awaitAll() }
        assertEquals(1,results.count { it!=null })
        val current=assertNotNull(MongoPlatformAccountRepository(db).findById("teacher"))
        assertEquals(setOf(UserRole.TEACHER),current.roles);assertEquals(0,current.authRevision)
        assertEquals(1,current.roleApplications.size);assertEquals(1,current.applicationRevision)
        assertNotNull(repo.submitStaffApplication("teacher",0,current.roleApplications.single()))
    }
    @Test fun `Mongo approval atomically persists audit request generations and changed scope is denied`()=database { db ->
        val repo=MongoPlatformAccountRepository(db);seed(repo);assertNotNull(repo.submitStaffApplication("teacher",0,request()))
        val command=AccountIdentityChange("approve","admin",AccountStatus.ACTIVE,setOf(UserRole.TEACHER,UserRole.TUTOR),"campus",now,"a".repeat(64),"request")
        val results=coroutineScope { (1..24).map { async { MongoPlatformAccountRepository(db).changeIdentity("teacher",0,command) } }.awaitAll() }
        assertTrue(results.all { it!=null })
        val approved=assertNotNull(MongoPlatformAccountRepository(db).findById("teacher"))
        assertEquals(1,approved.authRevision);assertEquals(1,approved.identityAudit.size)
        assertEquals(StaffApplicationStatus.APPROVED,approved.roleApplications.single().status)
        assertEquals(1,approved.roleGenerations[UserRole.TUTOR]);assertEquals(0,approved.roleGenerations[UserRole.TEACHER])
        assertNull(repo.changeIdentity("teacher",0,command.copy(scopeDigest="b".repeat(64))))
        assertNotNull(repo.changeIdentity("teacher",1,AccountIdentityChange("remove","admin",AccountStatus.ACTIVE,setOf(UserRole.TEACHER),"campus",now)))
        val regranted=assertNotNull(repo.changeIdentity("teacher",2,AccountIdentityChange("regrant","admin",AccountStatus.ACTIVE,setOf(UserRole.TEACHER,UserRole.TUTOR),"campus",now)))
        assertTrue(assertNotNull(regranted.roleGenerations[UserRole.TUTOR])>1)
    }
    @Test fun `corrupt Mongo role generation or application revision fails closed without coercion`()=database { db ->
        val repo=MongoPlatformAccountRepository(db);seed(repo)
        val collection=db.getCollection<Document>("platform_accounts")
        collection.updateOne(com.mongodb.client.model.Filters.eq("_id","teacher"),com.mongodb.client.model.Updates.set("roleGenerations",Document("TEACHER",0.5)))
        assertFalse(assertNotNull(repo.findById("teacher")).permitsSession)
        assertNull(repo.submitStaffApplication("teacher",0,request()))
        collection.updateOne(com.mongodb.client.model.Filters.eq("_id","teacher"),com.mongodb.client.model.Updates.combine(com.mongodb.client.model.Updates.set("roleGenerations",Document("TEACHER",0L)),com.mongodb.client.model.Updates.set("applicationRevision",0.5)))
        assertFalse(assertNotNull(repo.findById("teacher")).permitsSession)
        assertNull(repo.submitStaffApplication("teacher",0,request()))
    }
    private fun database(block:suspend(MongoDatabase)->Unit)=runBlocking {
        val uri=System.getenv("V12_TEST_MONGODB_URI");require(uri=="mongodb://127.0.0.1:27017")
        MongoClient.create(uri).use { client -> val db=client.getDatabase("v12_test_"+UUID.randomUUID().toString().replace("-",""));try { block(db) } finally { db.drop() } }
    }
}
