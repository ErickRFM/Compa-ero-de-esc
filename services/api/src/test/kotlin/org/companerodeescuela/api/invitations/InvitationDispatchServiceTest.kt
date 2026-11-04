package org.companerodeescuela.api.invitations

import kotlin.test.Test
import kotlin.test.assertTrue

class InvitationDispatchServiceTest {
    @Test
    fun `invitation command rejects invalid recipients`() {
        val cmd = InvitationDispatchCommand("not-an-email", "UPTLAX", InvitationProfile.STUDENT, "test-operation-123")
        assertTrue(!cmd.valid())
    }
}
