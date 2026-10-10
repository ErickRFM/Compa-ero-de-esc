package org.companerodeescuela.api.presence

import com.mongodb.kotlin.client.coroutine.*
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.companerodeescuela.shared.contracts.*
import kotlin.test.*

@EnabledIfEnvironmentVariable(named="V12_TEST_MONGODB_URI",matches=".+")
@Timeout(120)
class MongoSchoolPresenceScopeIntegrationTest {
    private fun value(id:String="day",institution:String?="campus",source:SchoolPresenceIdentitySource?=SchoolPresenceIdentitySource.NATIVE)=SchoolPresenceResponse(id,"student",1,1000,status=SchoolPresenceStatus.ACTIVE,qrVerified=true,networkVerified=false,networkVerificationMethod=NetworkVerificationMethod.SSID,institutionId=institution,identitySource=source,operationId=id)
    @Test fun `Mongo native school day selection never reuses or mutates same ID legacy institutional or foreign evidence`()=database { db ->
        val repo=MongoSchoolPresenceRepository(db)
        val legacy=value("legacy",null,null);val external=value("external",source=SchoolPresenceIdentitySource.INSTITUTIONAL);val foreign=value("foreign","other")
        for(row in listOf(legacy,external,foreign)) repo.save(row)
        val own=assertNotNull(repo.createNative(value().copy(startedAtEpochSeconds=2),10))
        assertEquals(own,repo.findActiveForNativeStudent("student","campus",10))
        assertNotEquals(SchoolPresenceIdentitySource.NATIVE,assertNotNull(repo.findActiveForStudent("student",10)).identitySource)
        assertEquals(legacy,repo.findById("legacy"));assertEquals(external,repo.findById("external"));assertEquals(foreign,repo.findById("foreign"))
    }
    @Test fun `independent Mongo replicas retain only one active native school day even across different operations`()=database { db ->
        val results=coroutineScope { (1..24).map { async { MongoSchoolPresenceRepository(db).createNative(value("operation-$it"),10) } }.awaitAll() }
        assertTrue(results.all { it!=null });assertEquals(1,results.map { it!!.id }.toSet().size)
        assertEquals(results.first(),MongoSchoolPresenceRepository(db).findActiveForNativeStudent("student","campus",10))
    }
    @Test fun `ended native days remain retained and old operation cannot create another grant`()=database { db ->
        val repo=MongoSchoolPresenceRepository(db);val old=assertNotNull(repo.createNative(value().copy(startedAtEpochSeconds=2),10))
        val next=assertNotNull(repo.createNative(value("next").copy(startedAtEpochSeconds=1100,expiresAtEpochSeconds=2000),1100))
        assertEquals(SchoolPresenceStatus.EXPIRED,repo.findById(old.id)!!.status)
        assertEquals(next,repo.findActiveForNativeStudent("student","campus",1100))
        repo.replace(next.copy(status=SchoolPresenceStatus.CLOSED,closedAtEpochSeconds=1200))
        assertNull(repo.createNative(value("next").copy(startedAtEpochSeconds=1201,expiresAtEpochSeconds=2200),1201))
        assertEquals(SchoolPresenceStatus.CLOSED,repo.findById("next")!!.status)
    }
    private fun database(block:suspend(MongoDatabase)->Unit)=runBlocking<Unit> {
        val uri=System.getenv("V12_TEST_MONGODB_URI");require(uri=="mongodb://127.0.0.1:27017")
        MongoClient.create(uri).use { client -> val db=client.getDatabase("v12_test_"+UUID.randomUUID().toString().replace("-",""));try { block(db) } finally { db.drop() } }
    }
}
