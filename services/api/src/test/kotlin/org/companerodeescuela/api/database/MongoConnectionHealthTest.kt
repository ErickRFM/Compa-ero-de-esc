package org.companerodeescuela.api.database

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.companerodeescuela.api.config.MongoSettings
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.measureTimedValue

@Timeout(value = 10, unit = TimeUnit.SECONDS)
class MongoConnectionHealthTest {
    @Test
    fun `the ping budget reports an unavailable MongoDB instead of cancelling its caller`() =
        runBlocking {
            unavailableMongo { uri ->
                DriverMongoConnectionFactory(pingTimeoutMs = 100).create(MongoSettings(uri, "readiness_test")).use { connection ->
                    connection.database()
                    assertFalse(withTimeout(2_000) { connection.ping() })
                }
            }
        }

    @Test
    fun `the configured selection budget overrides a longer URI timeout`() =
        runBlocking {
            unavailableMongo { uri ->
                val settings = MongoSettings(uri, "readiness_test", connectTimeoutMs = 50, serverSelectionTimeoutMs = 100)
                DriverMongoConnectionFactory(pingTimeoutMs = 2_000).create(settings).use { connection ->
                    connection.database()
                    val (reachable, elapsed) = measureTimedValue { withTimeout(4_000) { connection.ping() } }
                    assertFalse(reachable)
                    assertTrue(
                        elapsed.inWholeMilliseconds < 1_500,
                        "Configured server selection must finish before the independent ping budget: $elapsed",
                    )
                }
            }
        }

    @Test
    fun `an enclosing request cancellation still propagates`() =
        runBlocking {
            unavailableMongo { uri ->
                DriverMongoConnectionFactory(pingTimeoutMs = 5_000).create(MongoSettings(uri, "readiness_test")).use { connection ->
                    connection.database()
                    var pingReturned = false
                    assertFailsWith<TimeoutCancellationException> {
                        withTimeout(50) {
                            connection.ping()
                            pingReturned = true
                        }
                    }
                    assertFalse(pingReturned, "Caller cancellation must not become an unavailable result")
                }
            }
        }

    @Test
    @EnabledIfEnvironmentVariable(named = "V12_TEST_MONGODB_URI", matches = ".+")
    fun `a real synthetic local MongoDB still answers the readiness ping`() =
        runBlocking {
            val uri = requireNotNull(System.getenv("V12_TEST_MONGODB_URI"))
            require(Regex("""mongodb://(?:127\.0\.0\.1|localhost):[0-9]{1,5}/?""").matches(uri)) {
                "Readiness integration requires an explicit loopback lab"
            }
            DriverMongoConnectionFactory(pingTimeoutMs = 1_000).create(MongoSettings(uri, "readiness_test")).use { connection ->
                connection.database()
                assertTrue(withTimeout(5_000) { connection.ping() })
            }
        }

    @Test
    fun `explicit application connection budgets override URI options without losing other URI settings`() {
        val options =
            mongoClientSettings(
                MongoSettings(
                    "mongodb://127.0.0.1:27017/?connectTimeoutMS=9000&serverSelectionTimeoutMS=11000&socketTimeoutMS=4500&retryWrites=false",
                    "readiness_test",
                    connectTimeoutMs = 50,
                    serverSelectionTimeoutMs = 100,
                ),
            )
        assertEquals(50, options.socketSettings.getConnectTimeout(TimeUnit.MILLISECONDS))
        assertEquals(100L, options.clusterSettings.getServerSelectionTimeout(TimeUnit.MILLISECONDS))
        assertEquals(4500, options.socketSettings.getReadTimeout(TimeUnit.MILLISECONDS))
        assertFalse(options.retryWrites)
    }

    private suspend fun unavailableMongo(block: suspend (String) -> Unit) {
        // Reserve a local TCP listener that never speaks MongoDB. No remote host or
        // independently running service is needed to exercise driver selection.
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { socket ->
            block("mongodb://127.0.0.1:${socket.localPort}/?connectTimeoutMS=500&serverSelectionTimeoutMS=5000")
        }
    }
}
