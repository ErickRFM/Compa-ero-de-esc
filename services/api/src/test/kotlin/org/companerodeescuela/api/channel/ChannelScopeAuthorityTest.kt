package org.companerodeescuela.api.channel

import kotlinx.coroutines.test.runTest
import kotlin.test.*
import org.companerodeescuela.api.auth.*
import org.companerodeescuela.api.academic.groups.*
import org.companerodeescuela.api.integrations.mock.*
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.test.ScopedAcademicFixtures
import org.companerodeescuela.shared.contracts.*

class ChannelScopeAuthorityTest {
    @Test fun `native teacher with same external ID cannot publish or read institutional course by identity collision`()=runTest {
        val actor=UserSummary("T-0001","Native Teacher",roles=setOf(UserRole.TEACHER),institutionId="campus")
        val accounts=ScopedAcademicFixtures.accounts(actor)
        val policy=ChannelAccessPolicy(MockAcademicProvider(),accounts=accounts,groupRepository=InMemoryAcademicGroupRepository())
        assertTrue(policy.channelsFor(actor.id,actor.roles,actor,SessionIdentitySource.NATIVE).isEmpty())
        assertTrue(policy.channelsFor(actor.id,actor.roles,actor,SessionIdentitySource.INSTITUTIONAL).any { it.courseId=="C-9001" })
    }
    @Test fun `institutional administrator or coordinator has no global provider channel authority without explicit scope adapter`()=runTest {
        val policy=ChannelAccessPolicy(MockAcademicProvider())
        for(role in listOf(UserRole.ADMIN,UserRole.SUPER_ADMIN,UserRole.COORDINATOR)) {
            val actor=UserSummary("operator","Operator",roles=setOf(role),institutionId="campus")
            assertTrue(policy.channelsFor(actor.id,actor.roles,actor,SessionIdentitySource.INSTITUTIONAL).isEmpty())
        }
    }
    @Test fun `representatives channels are isolated by native institution and require current capability`()=runTest {
        val first=UserSummary("first","First Admin",roles=setOf(UserRole.ADMIN),institutionId="campus")
        val second=first.copy(id="second",institutionId="other")
        val accounts=ScopedAcademicFixtures.accounts(first,second)
        val groups=InMemoryAcademicGroupRepository();groups.create(ScopedAcademicFixtures.group("6A").copy(institutionId="campus"));groups.create(ScopedAcademicFixtures.group("6B").copy(institutionId="other"))
        val policy=ChannelAccessPolicy(MockAcademicProvider(),accounts=accounts,groupRepository=groups)
        val a=policy.channelsFor(first.id,first.roles,first,SessionIdentitySource.NATIVE)
        val b=policy.channelsFor(second.id,second.roles,second,SessionIdentitySource.NATIVE)
        assertEquals(1,a.size);assertEquals(1,b.size);assertTrue(a.none { channel -> b.any { it.id==channel.id } })
        assertTrue(a.single().canPublish);assertTrue(b.single().canPublish)
        assertFailsWith<ApiException.Unauthorized> { policy.channelsFor(first.id,first.roles,first.copy(institutionId="other"),SessionIdentitySource.NATIVE) }
    }
}
