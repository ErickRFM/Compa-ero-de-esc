package org.companerodeescuela.api.academic.groups

import java.time.Instant
import kotlinx.coroutines.*
import kotlin.test.*

class AcademicGroupScopePersistenceTest {
    private val now=Instant.parse("2026-10-10T07:00:00Z")
    private fun change()=AcademicGroupScopeChange("operation","owner","campus","period",now,now.plusSeconds(3600),now,"a".repeat(64))
    @Test fun `explicit legacy scope migration has one CAS audit and exact actor payload retry`()=runBlocking<Unit> {
        val repo=InMemoryAcademicGroupRepository();repo.create(AcademicGroupRecord("6A","6A",true,now))
        val results=coroutineScope { (1..24).map { async { repo.changeScope("6A",0,change()) } }.awaitAll() }
        assertTrue(results.all { it!=null })
        val current=assertNotNull(repo.find("6A"));assertEquals(1,current.scopeRevision);assertEquals(1,current.scopeAudit.size)
        assertEquals("campus",current.institutionId);assertEquals("period",current.periodId)
        assertNull(repo.changeScope("6A",0,change().copy(actorId="another")))
        assertNull(repo.changeScope("6A",0,change().copy(payloadDigest="b".repeat(64))))
        assertNull(repo.changeScope("6A",0,change().copy(endsAt=now.plusSeconds(7200))))
    }
    @Test fun `changing period and restoring labels never restores the old scope generation`()=runBlocking<Unit> {
        val repo=InMemoryAcademicGroupRepository();repo.create(AcademicGroupRecord("6A","6A",true,now))
        val first=assertNotNull(repo.changeScope("6A",0,change()))
        val second=assertNotNull(repo.changeScope("6A",first.scopeRevision,change().copy(operationId="next",periodId="next",payloadDigest="b".repeat(64))))
        val restored=assertNotNull(repo.changeScope("6A",second.scopeRevision,change().copy(operationId="restore",payloadDigest="c".repeat(64))))
        assertEquals(first.periodId,restored.periodId);assertTrue(restored.scopeRevision>first.scopeRevision)
        assertEquals(3,restored.scopeAudit.size)
    }
    @Test fun `invalid or stale scope cannot override actual group metadata`()=runBlocking<Unit> {
        val repo=InMemoryAcademicGroupRepository();repo.create(AcademicGroupRecord("6A","6A",true,now))
        assertNull(repo.changeScope("6A",1,change()))
        assertNull(repo.changeScope("6A",0,change().copy(institutionId="")))
        assertNull(repo.changeScope("6A",0,change().copy(endsAt=now)))
        assertEquals(0,assertNotNull(repo.find("6A")).scopeRevision)
    }
}
