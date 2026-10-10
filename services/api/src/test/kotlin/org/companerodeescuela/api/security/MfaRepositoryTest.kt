package org.companerodeescuela.api.security

import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import org.companerodeescuela.shared.contracts.StepUpAction

class MfaRepositoryTest {
    private val now=Instant.parse("2026-10-10T06:00:00Z")
    @Test fun `parallel confirmation and OTP step consumption each have one winner`() = runTest {
        val repo=InMemoryMfaRepository();assertTrue(repo.beginFactor(factor()))
        val confirmed=coroutineScope { (1..24).map { async(Dispatchers.Default) { repo.confirmFactor("actor","factor",100,now) } }.awaitAll() }
        assertEquals(1,confirmed.count { it })
        val used=coroutineScope { (1..24).map { async(Dispatchers.Default) { repo.consumeStep("actor","factor",101,now.plusSeconds(30)) } }.awaitAll() }
        assertEquals(1,used.count { it });assertEquals(101L,repo.findFactor("actor")?.lastUsedStep)
        assertFalse(repo.consumeStep("actor","factor",100,now.plusSeconds(60)))
        assertFalse(repo.beginFactor(factor().copy(id="replacement")))
    }
    @Test fun `expired confirmation and stale enrollment cannot replace an active factor`() = runTest {
        val repo=InMemoryMfaRepository();assertTrue(repo.beginFactor(factor()))
        assertFalse(repo.confirmFactor("actor","factor",100,now.plusSeconds(600)))
        assertTrue(repo.beginFactor(factor().copy(id="new",createdAt=now.plusSeconds(601),enrollmentExpiresAt=now.plusSeconds(1201))))
        assertFalse(repo.confirmFactor("actor","factor",120,now.plusSeconds(601)))
        assertTrue(repo.confirmFactor("actor","new",120,now.plusSeconds(601)))
        assertFalse(repo.beginFactor(factor().copy(id="replacement")))
    }
    @Test fun `proof only authorizes its exact operation and expires even on an idempotent retry`() = runTest {
        val repo=InMemoryMfaRepository();val binding=StepUpBinding("actor",4,StepUpAction.APPROVE_STAFF,"target","operation","A".repeat(64))
        assertTrue(repo.putProof(MfaProofRecord("hash",binding,now,now.plusSeconds(300))))
        assertFalse(repo.putProof(MfaProofRecord("hash",binding.copy(resourceId="other"),now,now.plusSeconds(300))))
        for(other in listOf(binding.copy(actorId="other"),binding.copy(authRevision=5),binding.copy(action=StepUpAction.ADMIN_INVITE),binding.copy(resourceId="other"),binding.copy(operationId="other"),binding.copy(payloadDigest="B".repeat(64)))) assertFalse(repo.consumeProof("hash",other,now))
        assertTrue(repo.consumeProof("hash",binding,now));assertTrue(repo.consumeProof("hash",binding,now.plusSeconds(1)))
        assertFalse(repo.consumeProof("hash",binding,now.plusSeconds(300)))
    }
    private fun factor()=MfaFactor("factor","actor","encrypted",4,now,now.plusSeconds(600))
}
