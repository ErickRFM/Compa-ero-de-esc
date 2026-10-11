package org.companerodeescuela.api.devices

import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@EnabledIfEnvironmentVariable(named = "V12_TEST_MONGODB_URI", matches = ".+")
@Timeout(120)
class MongoPushOutboxIntegrationTest {
    private fun withDatabase(block: suspend (MongoDatabase) -> Unit) = runBlocking {
        val uri = System.getenv("V12_TEST_MONGODB_URI")
        require(uri == "mongodb://127.0.0.1:27017")
        MongoClient.create(uri).use { client ->
            val db = client.getDatabase("v13_push_" + UUID.randomUUID().toString().replace("-", ""))
            try { block(db) } finally { db.drop() }
        }
    }

    private fun item(seed: String) = PushOutboxItem(seed.repeat(64),"NATIVE:student-a",
        "fcm_test_registration_token_12345678", "Aviso académico", "Tienes un nuevo aviso")

    @Test fun `concurrent Mongo workers claim a destination at most once`() = withDatabase { db ->
        val event = item("a")
        val workerA = MongoPushOutboxRepository(db)
        val workerB = MongoPushOutboxRepository(db)
        assertTrue(workerA.enqueue(event))
        assertFalse(workerB.enqueue(event))
        val now = Instant.parse("2030-01-01T00:00:00Z")
        val results = coroutineScope {
            (0 until 24).map { index ->
                async { if (index % 2 == 0) workerA.claim(now) else workerB.claim(now) }
            }.awaitAll()
        }
        assertEquals(1, results.count { it != null })
        val claimed = assertNotNull(results.firstOrNull { it != null })
        assertEquals(1, claimed.attempts)
        assertTrue(workerB.finish(claimed, PushDeliveryResult.ACCEPTED, now))
        assertNull(workerA.claim(now.plusSeconds(500)))
    }

    @Test fun `expired Mongo lease can be reclaimed but old worker cannot acknowledge it`() = withDatabase { db ->
        val repo = MongoPushOutboxRepository(db)
        assertTrue(repo.enqueue(item("b")))
        val now = Instant.parse("2030-01-01T00:00:00Z")
        val first = assertNotNull(repo.claim(now))
        assertNull(repo.claim(now.plusSeconds(89)))
        val next = assertNotNull(MongoPushOutboxRepository(db).claim(now.plusSeconds(91)))
        assertFalse(repo.finish(first, PushDeliveryResult.ACCEPTED, now.plusSeconds(91)))
        assertTrue(repo.finish(next, PushDeliveryResult.RETRYABLE, now.plusSeconds(92)))
        assertNull(repo.claim(now.plusSeconds(120)))
        val third = assertNotNull(repo.claim(now.plusSeconds(154)))
        assertEquals(3, third.attempts)
        assertTrue(repo.finish(third, PushDeliveryResult.INVALID_TOKEN, now.plusSeconds(154)))
        assertNull(repo.claim(now.plusSeconds(180)))
    }

    @Test fun `Mongo outbox keeps idempotent recipient records after permanent rejection`() = withDatabase { db ->
        val repo = MongoPushOutboxRepository(db)
        val task = item("c")
        assertTrue(repo.enqueue(task))
        val now = Instant.parse("2030-01-01T00:00:00Z")
        val claimed = assertNotNull(repo.claim(now))
        assertTrue(repo.finish(claimed, PushDeliveryResult.REJECTED, now))
        assertFalse(MongoPushOutboxRepository(db).enqueue(task))
        assertNull(repo.claim(now.plusSeconds(3600)))
    }
}
