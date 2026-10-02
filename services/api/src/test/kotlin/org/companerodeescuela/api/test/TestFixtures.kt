package org.companerodeescuela.api.test

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.api.config.Environment
import org.companerodeescuela.api.config.MongoSettings
import org.companerodeescuela.api.database.MongoConnection

/**
 * Test doubles shared by the API test suite.
 *
 * `FakeMongoConnection` is what keeps unit tests independent from a running
 * MongoDB instance: `ping()` returns a scripted answer, and `database()` fails
 * loudly so no test can accidentally start depending on a real driver.
 */
object TestFixtures {

    val FIXED_INSTANT: Instant = Instant.parse("2026-03-01T10:15:30Z")

    val FIXED_CLOCK: Clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC)

    /**
     * Builds settings for tests. Pass a non-blank [mongoUri] to make the
     * settings look like a configured database.
     */
    fun settings(
        environment: Environment = Environment.LOCAL,
        mongoUri: String = "",
    ): ApiSettings = ApiSettings(
        serviceName = "companero-api",
        environment = environment,
        host = "127.0.0.1",
        port = 8080,
        version = "0.1.0-test",
        apiVersion = "v1",
        mongo = MongoSettings(
            uri = mongoUri,
            databaseName = "companero_test",
        ),
        jwtSecret = null,
        jwtIssuer = "companero-de-escuela",
        jwtAudience = "companero-android",
    )
}

/** [MongoConnection] whose ping result is scripted by the test. */
class FakeMongoConnection(
    private val reachable: Boolean,
    override val databaseName: String = "companero_test",
) : MongoConnection {

    var closeCount: Int = 0
        private set

    override suspend fun ping(): Boolean = reachable

    override fun database(): MongoDatabase =
        error("FakeMongoConnection does not expose a real database")

    override fun close() {
        closeCount++
    }
}
