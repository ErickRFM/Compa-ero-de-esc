package org.companerodeescuela.api.tutoring

import java.time.Instant
import kotlinx.coroutines.*
import kotlin.test.*
import org.companerodeescuela.api.errors.ApiException

class TutorAssignmentPersistenceTest {
    private val now=Instant.parse("2026-10-10T07:00:00Z")
    private fun record()=TutorAssignmentRecord("operation-assignment","tutor","6A",true,"admin",now,institutionId="campus",periodId="period",startsAt=now,endsAt=now.plusSeconds(600),tutorRoleGeneration=2)
    @Test fun `assignment retry is bound to full original ID scope and role generation`()=runBlocking {
        val repo=InMemoryTutorAssignmentRepository();val record=record();assertEquals(record,repo.create(record))
        val results=coroutineScope { (1..24).map { async { repo.create(record) } }.awaitAll() }
        assertTrue(results.all { it==record });assertEquals(1,repo.listAll().size)
        for(changed in listOf(record.copy(id="different-operation"),record.copy(institutionId="other"),record.copy(periodId="new-period"),record.copy(endsAt=now.plusSeconds(700)),record.copy(tutorRoleGeneration=3))) assertFailsWith<ApiException.Conflict> { repo.create(changed) }
        assertEquals(record,repo.findActive("tutor","6A"))
    }
    @Test fun `reassignment uses a new explicit operation and never overwrites a revoked ID`()=runBlocking {
        val repo=InMemoryTutorAssignmentRepository();val record=record();repo.create(record)
        val revoked=assertNotNull(repo.revoke(record.id,"admin",now))
        assertFailsWith<ApiException.Conflict> { repo.create(record) }
        assertEquals(revoked,repo.findById(record.id))
        val replacement=record.copy(id="replacement",tutorRoleGeneration=4)
        assertEquals(replacement,repo.create(replacement));assertEquals(2,repo.listAll().size)
    }
}
