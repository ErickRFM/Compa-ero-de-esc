package org.companerodeescuela.api.academic.groups

import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Previously granted enrollment cannot silently survive a change of academic term. */
class AcademicMembershipRevisionTest {
    private val now=Instant.parse("2026-10-10T00:00:00Z")
    private fun change(operation:String,period:String)=AcademicGroupScopeChange(
        operation,"super-admin","qa-campus",period,now,now.plusSeconds(86400),
        now,"a".repeat(64))

    @Test fun `migrating a group disables inherited memberships until explicitly reassigned`()=runBlocking {
        val repo=InMemoryAcademicGroupRepository()
        repo.create(AcademicGroupRecord("9A","9A",true,now))
        repo.assign(AcademicGroupMembershipRecord("9A","student",now))
        assertTrue(repo.isUserInGroup("student","9A"))
        val first=assertNotNull(repo.changeScope("9A",0,change("start","2026-A")))
        assertFalse(repo.isUserInGroup("student","9A"))
        assertTrue(repo.memberIdsForGroup("9A").isEmpty())
        assertTrue(repo.listGroupsForUser("student").isEmpty())
        val newMembership=AcademicGroupMembershipRecord("9A","student",now,"admin","enroll-1","b".repeat(64),first.scopeRevision)
        assertEquals(newMembership,repo.assign(newMembership))
        assertTrue(repo.isUserInGroup("student","9A"))
        val second=assertNotNull(repo.changeScope("9A",first.scopeRevision,change("change-period","2026-B")))
        assertFalse(repo.isUserInGroup("student","9A"))
        // Returning to a previously used label still increments the revision.
        val restored=assertNotNull(repo.changeScope("9A",second.scopeRevision,change("restore","2026-A")))
        assertFalse(repo.isUserInGroup("student","9A"))
        assertEquals(restored.scopeRevision,repo.assign(newMembership.copy(operationId="enroll-2",groupScopeRevision=restored.scopeRevision)).groupScopeRevision)
        assertTrue(repo.isUserInGroup("student","9A"))
    }
}
