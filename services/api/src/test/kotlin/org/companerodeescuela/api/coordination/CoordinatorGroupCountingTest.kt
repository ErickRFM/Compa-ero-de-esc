package org.companerodeescuela.api.coordination

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import org.companerodeescuela.api.academic.groups.AcademicGroupRecord

class CoordinatorGroupCountingTest {
    private val now = Instant.parse("2026-10-12T12:00:00Z")
    private fun group(id: String, tenant: String?, active: Boolean = true,
        start: Instant = now.minusSeconds(3600), end: Instant = now.plusSeconds(3600), rev: Long = 1) =
        AcademicGroupRecord(id, id, active, now.minusSeconds(10000), tenant, "p1", start, end, rev)

    @Test fun `counts only current active scoped groups`() {
        val groups = listOf(group("a","U"), group("b","U"), group("c","OTHER"),
            group("d","U",active=false),group("e","U",end=now),
            group("f","U",start=now.plusSeconds(1)),group("g",null),group("h","U",rev=-1))
        assertEquals(2, countActiveGroupsInInstitution(groups, "U", now))
        assertEquals(1, countActiveGroupsInInstitution(groups, "OTHER", now))
        assertEquals(0, countActiveGroupsInInstitution(groups, "UNKNOWN", now))
    }
}
