package org.companerodeescuela.api.auth

import java.time.Instant
import kotlinx.coroutines.*
import kotlin.test.*
import org.companerodeescuela.shared.contracts.*

class StaffApplicationPersistenceTest {
    private val now=Instant.parse("2026-10-10T07:00:00Z")
    private fun request(id:String="request")=StaffApplication(id,StaffRole.TUTOR,"campus","reference","6A",now.epochSecond)
    private fun teacher()=PlatformAccount("teacher","Teacher","teacher@example.test","test-hash",setOf(UserRole.TEACHER),emailVerifiedAt=now,institutionId="campus")
    @Test fun `supplementary tutor request is separate from grants and is exact retryable`()=runBlocking {
        val repo=InMemoryPlatformAccountRepository();assertTrue(repo.create(teacher()))
        val submitted=assertNotNull(repo.submitStaffApplication("teacher",0,request()))
        assertEquals(setOf(UserRole.TEACHER),submitted.roles);assertEquals(0,submitted.authRevision)
        assertEquals(listOf(request()),submitted.roleApplications);assertEquals(1,submitted.applicationRevision)
        assertNotNull(repo.submitStaffApplication("teacher",0,request()))
        assertNull(repo.submitStaffApplication("teacher",0,request().copy(preferredGroup="other")))
        assertNull(repo.submitStaffApplication("teacher",0,request("duplicate-pending")))
        assertEquals(1,assertNotNull(repo.findById("teacher")).roleApplications.size)
    }
    @Test fun `staff decision CAS binds application digest and one operation while preserving teacher role`()=runBlocking {
        val repo=InMemoryPlatformAccountRepository();repo.create(teacher())
        assertNotNull(repo.submitStaffApplication("teacher",0,request()))
        val change=AccountIdentityChange("approve","admin",AccountStatus.ACTIVE,setOf(UserRole.TEACHER,UserRole.TUTOR),"campus",now,scopeDigest="a".repeat(64),approvedApplicationId="request")
        val results=coroutineScope { (1..24).map { async { repo.changeIdentity("teacher",0,change) } }.awaitAll() }
        assertTrue(results.all { it!=null });val approved=assertNotNull(repo.findById("teacher"))
        assertEquals(1,approved.authRevision);assertEquals(1,approved.identityAudit.size)
        assertEquals(StaffApplicationStatus.APPROVED,approved.roleApplications.single().status)
        assertEquals(0,approved.roleGenerations[UserRole.TEACHER]);assertEquals(1,approved.roleGenerations[UserRole.TUTOR])
        assertEquals(1,approved.roleApplications.single().grantedRoleGeneration)
        assertNull(repo.changeIdentity("teacher",0,change.copy(scopeDigest="b".repeat(64))))
        assertNull(repo.changeIdentity("teacher",1,change.copy(operationId="approve-again")))
    }
    @Test fun `revoking and granting tutor never revives old grant generation or retained delegation`()=runBlocking {
        val repo=InMemoryPlatformAccountRepository();repo.create(teacher().copy(roles=setOf(UserRole.TEACHER,UserRole.TUTOR,UserRole.ADMIN),roleGenerations=mapOf(UserRole.TEACHER to 2,UserRole.TUTOR to 3,UserRole.ADMIN to 4),adminCapabilities=setOf(AdminCapability.MANAGE_USERS)))
        val removed=assertNotNull(repo.changeIdentity("teacher",0,AccountIdentityChange("remove","super",AccountStatus.ACTIVE,setOf(UserRole.TEACHER),"campus",now)))
        assertTrue(removed.adminCapabilities.isEmpty());assertEquals(2,removed.roleGenerations[UserRole.TEACHER])
        val granted=assertNotNull(repo.changeIdentity("teacher",1,AccountIdentityChange("regrant","super",AccountStatus.ACTIVE,setOf(UserRole.TEACHER,UserRole.TUTOR,UserRole.ADMIN),"campus",now)))
        assertTrue(assertNotNull(granted.roleGenerations[UserRole.TUTOR])>3);assertTrue(granted.adminCapabilities.isEmpty())
        assertEquals(2,granted.roleGenerations[UserRole.TEACHER])
    }
    @Test fun `unverified suspended stale and privileged application payloads cannot submit a request`()=runBlocking {
        val repo=InMemoryPlatformAccountRepository();repo.create(teacher().copy(emailVerifiedAt=null))
        assertNull(repo.submitStaffApplication("teacher",0,request()))
        val verified=InMemoryPlatformAccountRepository();verified.create(teacher())
        assertNull(verified.submitStaffApplication("teacher",1,request()))
        assertNull(verified.submitStaffApplication("teacher",0,request().copy(status=StaffApplicationStatus.APPROVED,decidedBy="self",grantedRoleGeneration=9)))
        assertNotNull(verified.changeIdentity("teacher",0,AccountIdentityChange("suspend","admin",AccountStatus.SUSPENDED,setOf(UserRole.TEACHER),"campus",now)))
        assertNull(verified.submitStaffApplication("teacher",1,request()))
    }
}
