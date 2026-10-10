package org.companerodeescuela.api.admin

import java.time.*
import kotlin.test.*
import kotlinx.coroutines.test.runTest
import org.companerodeescuela.api.auth.*
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.*

class InstitutionScopePolicyTest {
    private val now=Instant.parse("2026-10-10T07:00:00Z")
    private fun admin(id:String="admin")=PlatformAccount(id,"QA Admin","$id@example.test","test-hash",setOf(UserRole.ADMIN),institutionId="campus",emailVerifiedAt=now,adminCapabilities=setOf(AdminCapability.APPROVE_STAFF))
    @Test fun `institutional admin has only explicitly delegated capability within its own actual institution`()=runTest {
        val repo=InMemoryPlatformAccountRepository();val account=admin();repo.create(account)
        val policy=InstitutionScopePolicy(repo,Clock.fixed(now,ZoneOffset.UTC));val actor=account.toUserSummary()
        assertEquals("admin",policy.require(actor,AdminCapability.APPROVE_STAFF,"campus").id)
        assertFailsWith<ApiException.Forbidden> { policy.require(actor,AdminCapability.APPROVE_STAFF,"other-campus") }
        assertFailsWith<ApiException.Forbidden> { policy.require(actor,AdminCapability.MANAGE_USERS,"campus") }
        assertFailsWith<ApiException.Forbidden> { policy.require(actor,AdminCapability.APPROVE_STAFF,"") }
    }
    @Test fun `revoked actor stale revision forged roles and unverified owner cannot administer`()=runTest {
        val repo=InMemoryPlatformAccountRepository();val account=admin();repo.create(account)
        val policy=InstitutionScopePolicy(repo,Clock.fixed(now,ZoneOffset.UTC));val actor=account.toUserSummary()
        assertFailsWith<ApiException.Unauthorized> { policy.require(actor.copy(roles=setOf(UserRole.SUPER_ADMIN)),AdminCapability.MANAGE_USERS,"other") }
        assertNotNull(repo.changeIdentity("admin",0,AccountIdentityChange("suspend","owner",AccountStatus.SUSPENDED,account.roles,"campus",now)))
        assertFailsWith<ApiException.Unauthorized> { policy.require(actor,AdminCapability.APPROVE_STAFF,"campus") }
        val unverified=admin("unverified").copy(emailVerifiedAt=null);repo.create(unverified)
        assertFailsWith<ApiException.Forbidden> { policy.require(unverified.toUserSummary(),AdminCapability.APPROVE_STAFF,"campus") }
    }
    @Test fun `verified super administrator may act across institutions but student or tutor never inherits admin scope`()=runTest {
        val repo=InMemoryPlatformAccountRepository();val owner=admin("owner").copy(roles=setOf(UserRole.SUPER_ADMIN),institutionId=null,adminCapabilities=emptySet());repo.create(owner)
        val policy=InstitutionScopePolicy(repo,Clock.fixed(now,ZoneOffset.UTC))
        assertEquals(owner.id,policy.require(owner.toUserSummary(),AdminCapability.MANAGE_USERS,"other-campus").id)
        for(role in listOf(UserRole.STUDENT,UserRole.TEACHER,UserRole.TUTOR,UserRole.WORKSHOP_PARTICIPANT)) {
            val account=admin(role.name).copy(roles=setOf(role));repo.create(account)
            assertFailsWith<ApiException.Forbidden> { policy.require(account.toUserSummary(),AdminCapability.APPROVE_STAFF,"campus") }
        }
    }
}
