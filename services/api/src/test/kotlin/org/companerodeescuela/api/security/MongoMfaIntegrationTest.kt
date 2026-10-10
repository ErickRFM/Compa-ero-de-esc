package org.companerodeescuela.api.security

import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.*
import org.bson.Document
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.Timeout
import org.companerodeescuela.shared.contracts.StepUpAction
import kotlin.test.*

@EnabledIfEnvironmentVariable(named="V12_TEST_MONGODB_URI",matches=".+")
@Timeout(120)
class MongoMfaIntegrationTest {
    private val now=Instant.parse("2026-10-10T06:00:00Z")
    private fun factor(id:String="factor")=MfaFactor(id,"actor","encrypted-test-material",4,now,now.plusSeconds(600))
    @Test fun `independent Mongo repositories admit only one confirmation and one OTP use`()=database { db ->
        val first=MongoMfaRepository(db);val second=MongoMfaRepository(db)
        assertTrue(first.beginFactor(factor()))
        val confirmed=coroutineScope { (1..24).map { n -> async { (if(n%2==0) first else second).confirmFactor("actor","factor",100,now) } }.awaitAll() }
        assertEquals(1,confirmed.count { it })
        val used=coroutineScope { (1..24).map { n -> async { (if(n%2==0) first else second).consumeStep("actor","factor",101,now.plusSeconds(30)) } }.awaitAll() }
        assertEquals(1,used.count { it })
        assertFalse(second.consumeStep("actor","factor",100,now.plusSeconds(31)))
        assertFalse(second.beginFactor(factor("replacement")))
        assertEquals(101,assertNotNull(MongoMfaRepository(db).findFactor("actor")).lastUsedStep)
    }
    @Test fun `pending enrollment replacement expiry and malformed authority fail closed in real Mongo`()=database { db ->
        val repo=MongoMfaRepository(db)
        assertTrue(repo.beginFactor(factor()))
        assertTrue(MongoMfaRepository(db).beginFactor(factor("new-factor")))
        assertFalse(repo.confirmFactor("actor","factor",100,now))
        assertFalse(repo.confirmFactor("actor","new-factor",100,now.plusSeconds(600)))
        assertNull(repo.findFactor("missing"))
        assertEquals("encrypted-test-material",assertNotNull(repo.findFactor("actor")).encryptedSecret)
        db.getCollection<Document>("platform_mfa_factors").updateOne(com.mongodb.client.model.Filters.eq("_id","actor"),com.mongodb.client.model.Updates.set("lastUsedStep",100.5))
        assertNull(MongoMfaRepository(db).findFactor("actor"))
        assertFalse(repo.confirmFactor("actor","new-factor",101,now))
        assertFalse(repo.beginFactor(factor("repair-without-authority")))
    }
    @Test fun `real Mongo proof persists exact command retry and rejects changed scope or expiry`()=database { db ->
        val binding=StepUpBinding("actor",4,StepUpAction.APPROVE_STAFF,"target","operation","a".repeat(64))
        val proof=MfaProofRecord("hash",binding,now,now.plusSeconds(300))
        val first=MongoMfaRepository(db)
        assertTrue(first.putProof(proof));assertFalse(first.putProof(proof))
        val restored=MongoMfaRepository(db)
        for(changed in listOf(binding.copy(actorId="other"),binding.copy(authRevision=5),binding.copy(action=StepUpAction.ADMIN_AUTHORITY),binding.copy(resourceId="other"),binding.copy(operationId="other"),binding.copy(payloadDigest="b".repeat(64)))) assertFalse(restored.consumeProof("hash",changed,now))
        val retries=coroutineScope { (1..24).map { async { MongoMfaRepository(db).consumeProof("hash",binding,now.plusSeconds(1)) } }.awaitAll() }
        assertTrue(retries.all { it })
        assertFalse(restored.consumeProof("hash",binding,now.plusSeconds(300)))
        assertFalse(restored.consumeProof("hash",binding,now.minusSeconds(1)))
        db.getCollection<Document>("platform_mfa_proofs").updateOne(com.mongodb.client.model.Filters.eq("_id","hash"),com.mongodb.client.model.Updates.set("authRevision",4.5))
        assertFalse(restored.consumeProof("hash",binding,now.plusSeconds(2)))
    }
    private fun database(block:suspend(MongoDatabase)->Unit)=runBlocking {
        val uri=System.getenv("V12_TEST_MONGODB_URI")
        require(uri=="mongodb://127.0.0.1:27017") { "Integration tests require isolated loopback Mongo" }
        MongoClient.create(uri).use { client ->
            val db=client.getDatabase("v12_test_"+UUID.randomUUID().toString().replace("-",""))
            try { block(db) } finally { db.drop() }
        }
    }
}
