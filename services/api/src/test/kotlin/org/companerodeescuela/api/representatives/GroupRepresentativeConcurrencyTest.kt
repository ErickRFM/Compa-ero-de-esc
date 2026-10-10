package org.companerodeescuela.api.representatives

import com.mongodb.kotlin.client.coroutine.MongoClient
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.*
import org.companerodeescuela.shared.contracts.*
import org.companerodeescuela.api.errors.ApiException

private fun appointment(index:Int)=GroupRepresentativeAssignment(
    id="appointment-$index",academicGroupId="9A",academicGroupName="9A",
    institutionId="campus",academicTermId="2026-A",studentUserId="student-$index",
    position=RepresentativePosition.CHIEF,appointedBy="tutor",appointedAtEpochSeconds=1L,
    status=RepresentativeStatus.PENDING,version=1L,groupScopeRevision=1)

class GroupRepresentativeConcurrencyTest {
    @Test fun `simultaneous pending chief appointments result in a single winner in memory`()=runBlocking {
        val repo=InMemoryGroupRepresentativeRepository()
        val results=coroutineScope { (1..24).map { n -> async(Dispatchers.Default) {
            runCatching { repo.save(appointment(n)) }
        } }.awaitAll() }
        assertEquals(1,results.count { it.isSuccess })
        assertTrue(results.filter { it.isFailure }.all { it.exceptionOrNull() is ApiException.Conflict })
        assertEquals(1,repo.listByGroup("9A").size)
    }
}

@EnabledIfEnvironmentVariable(named="V12_TEST_MONGODB_URI",matches=".+")
@Timeout(120)
class MongoGroupRepresentativeConcurrencyTest {
    @Test fun `multiple Mongo instances cannot appoint duplicate pending chief`()=runBlocking {
        val uri=System.getenv("V12_TEST_MONGODB_URI")
        require(uri=="mongodb://127.0.0.1:27017")
        MongoClient.create(uri).use { client ->
            val db=client.getDatabase("v12_rep_"+UUID.randomUUID().toString().replace("-",""))
            try {
                val results=coroutineScope { (1..24).map { n -> async(Dispatchers.Default) {
                    runCatching { MongoGroupRepresentativeRepository(db).save(appointment(n)) }
                } }.awaitAll() }
                assertEquals(1,results.count { it.isSuccess })
                assertTrue(results.filter { it.isFailure }.all { it.exceptionOrNull() is ApiException.Conflict })
                assertEquals(1,MongoGroupRepresentativeRepository(db).listByGroup("9A").size)
            } finally { db.drop() }
        }
    }
}
