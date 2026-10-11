package org.companerodeescuela.api.devices

import com.mongodb.kotlin.client.coroutine.MongoClient
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import org.companerodeescuela.api.errors.ApiException

private fun fcmToken(value:String):String = "fcm_device_registration_token_" + value.repeat(4)

class DeviceTokenRegistryTest {
    @Test fun `token rotation and account switching never retain a previous destination`()=runBlocking {
        val repo=InMemoryDeviceTokenRepository()
        val old=fcmToken("OLD")
        val fresh=fcmToken("NEW")
        repo.register("NATIVE:alice",old,"android","Pixel","phone-1")
        assertEquals(listOf(old),repo.listTokens(setOf("NATIVE:alice")))
        repo.register("NATIVE:alice",fresh,"android","Pixel","phone-1")
        assertEquals(listOf(fresh),repo.listTokens(setOf("NATIVE:alice")))
        repo.register("NATIVE:bob",fresh,"android","Pixel","phone-2")
        assertTrue(repo.listTokens(setOf("NATIVE:alice")).isEmpty())
        assertFalse(repo.unregister("NATIVE:alice",fresh))
        assertEquals(listOf(fresh),repo.listTokens(setOf("NATIVE:bob")))
        assertTrue(repo.deactivateInvalid(fresh))
        assertTrue(repo.listTokens(setOf("NATIVE:bob")).isEmpty())
    }

    @Test fun `session binding rotates with a device and remains owner scoped`()=runBlocking {
        val repo=InMemoryDeviceTokenRepository()
        val token=fcmToken("SESSION")
        repo.register("NATIVE:alice",token,"android",null,"phone-1","session-a")
        assertEquals("session-a",repo.sessionIdFor("NATIVE:alice",token))
        assertEquals(null,repo.sessionIdFor("NATIVE:bob",token))
        repo.register("NATIVE:alice",token,"android",null,"phone-1","session-b")
        assertEquals("session-b",repo.sessionIdFor("NATIVE:alice",token))
    }

    @Test fun `invalid device identifiers and malformed push tokens fail closed`()=runBlocking {
        val repo=InMemoryDeviceTokenRepository()
        assertFailsWith<ApiException.Validation> {
            repo.register("NATIVE:alice","short","android",null,"device-1")
        }
        assertFailsWith<ApiException.Validation> {
            repo.register("NATIVE:alice",fcmToken("X"),"android",null,"../injection")
        }
        assertTrue(repo.listTokens(setOf("NATIVE:alice")).isEmpty())
    }
}

@EnabledIfEnvironmentVariable(named="V12_TEST_MONGODB_URI",matches=".+")
@Timeout(120)
class MongoDeviceTokenRegistryIntegrationTest {
    private fun database(block:suspend(com.mongodb.kotlin.client.coroutine.MongoDatabase)->Unit)=runBlocking {
        val uri=System.getenv("V12_TEST_MONGODB_URI")
        require(uri=="mongodb://127.0.0.1:27017")
        MongoClient.create(uri).use { client ->
            val db=client.getDatabase("v13_fcm_"+UUID.randomUUID().toString().replace("-",""))
            try { block(db) } finally { db.drop() }
        }
    }

    @Test fun `Mongo registry persists rotated and revoked tokens across repository instances`()=database { db ->
        val alice=MongoDeviceTokenRepository(db)
        val old=fcmToken("OLD")
        val fresh=fcmToken("NEW")
        alice.register("NATIVE:alice",old,"android",null,"phone-1")
        MongoDeviceTokenRepository(db).register("NATIVE:alice",fresh,"android",null,"phone-1")
        assertEquals(listOf(fresh),MongoDeviceTokenRepository(db).listTokens(setOf("NATIVE:alice")))
        assertFalse(alice.unregister("NATIVE:bob",fresh))
        assertTrue(alice.unregister("NATIVE:alice",fresh))
        assertTrue(MongoDeviceTokenRepository(db).listTokens(setOf("NATIVE:alice")).isEmpty())
    }

    @Test fun `Mongo registrations retain only current owner and refresh session binding`()=database { db ->
        val repo=MongoDeviceTokenRepository(db)
        val token=fcmToken("BOUND")
        repo.register("NATIVE:alice",token,"android",null,"phone-1","session-1")
        assertEquals("session-1",MongoDeviceTokenRepository(db).sessionIdFor("NATIVE:alice",token))
        assertEquals(null,repo.sessionIdFor("NATIVE:bob",token))
        repo.register("NATIVE:bob",token,"android",null,"phone-2","session-2")
        assertEquals(null,repo.sessionIdFor("NATIVE:alice",token))
        assertEquals("session-2",repo.sessionIdFor("NATIVE:bob",token))
    }

    @Test fun `Mongo registry cannot deliver a transferred token to the old account`()=database { db ->
        val token=fcmToken("TRANSFER")
        val first=MongoDeviceTokenRepository(db)
        first.register("NATIVE:alice",token,"android",null,"alice-phone")
        MongoDeviceTokenRepository(db).register("NATIVE:bob",token,"android",null,"bob-phone")
        assertTrue(first.listTokens(setOf("NATIVE:alice")).isEmpty())
        assertEquals(listOf(token),first.listTokens(setOf("NATIVE:bob")))
        assertFalse(first.unregister("NATIVE:alice",token))
        assertTrue(first.deactivateInvalid(token))
        assertTrue(first.listTokens(setOf("NATIVE:bob")).isEmpty())
    }
}
