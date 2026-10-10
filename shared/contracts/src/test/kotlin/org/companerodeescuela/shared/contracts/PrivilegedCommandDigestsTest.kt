package org.companerodeescuela.shared.contracts

import kotlin.test.*

class PrivilegedCommandDigestsTest {
    @Test fun `approval proof digest binds actual target all scope fields and operation but excludes proof material`() {
        val request=ApproveStaffRequest("operation",3,"application","campus",listOf(TutorApprovalScope("6A","period",100,600)),"private-proof")
        val digest=PrivilegedCommandDigests.staffApproval("target",request)
        assertTrue(Regex("[a-f0-9]{64}").matches(digest))
        assertEquals(digest,PrivilegedCommandDigests.staffApproval("target",request.copy(proof="another-proof")))
        assertNotEquals(digest,PrivilegedCommandDigests.staffApproval("other",request))
        for(changed in listOf(request.copy(operationId="other"),request.copy(expectedAuthRevision=4),request.copy(applicationId="other"),request.copy(institutionId="other"),request.copy(tutorScopes=listOf(TutorApprovalScope("6B","period",100,600))),request.copy(tutorScopes=listOf(TutorApprovalScope("6A","other",100,600))),request.copy(tutorScopes=listOf(TutorApprovalScope("6A","period",100,601))))) assertNotEquals(digest,PrivilegedCommandDigests.staffApproval("target",changed))
    }
    @Test fun `lifecycle digest binds status target and revision without including private evidence`() {
        val request=AccountLifecycleRequest("operation",2,AccountStatus.SUSPENDED,"proof")
        val digest=PrivilegedCommandDigests.accountLifecycle("target",request)
        assertEquals(digest,PrivilegedCommandDigests.accountLifecycle("target",request.copy(proof="other")))
        assertNotEquals(digest,PrivilegedCommandDigests.accountLifecycle("other",request))
        assertNotEquals(digest,PrivilegedCommandDigests.accountLifecycle("target",request.copy(status=AccountStatus.ACTIVE)))
        assertNotEquals(digest,PrivilegedCommandDigests.accountLifecycle("target",request.copy(expectedAuthRevision=3)))
        assertNotEquals(digest,PrivilegedCommandDigests.staffApproval("target",ApproveStaffRequest("operation",2,"application","campus",proof="proof")))
    }
}
