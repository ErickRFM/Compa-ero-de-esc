package org.companerodeescuela.api.database

import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.bson.Document
import org.companerodeescuela.api.config.MongoSettings
import org.slf4j.LoggerFactory

/**
 * Owns the lifecycle of the MongoDB client.
 *
 * One instance exists per application. It is created during boot and closed on
 * shutdown, so the connection pool is never leaked and never shared across
 * unrelated processes.
 *
 * This interface is the seam that keeps the rest of the API free of driver
 * types: repositories receive a [MongoConnection], never a `MongoClient`.
 */
interface MongoConnectionFactory {
    /**
     * Opens the connection described by [settings].
     *
     * Implementations must be lazy: creating a `MongoClient` performs no I/O,
     * but the first operation does, and that latency must not be paid during
     * boot.
     */
    fun create(settings: MongoSettings): MongoConnection
}

/**
 * A live, closeable handle to the database.
 */
interface MongoConnection : AutoCloseable {
    /** Name of the logical database this connection points at. */
    val databaseName: String

    /**
     * Executes a lightweight `ping` against the server.
     *
     * Used by `GET /health` and `GET /ready`. Implementations must apply their
     * own timeout so a hanging server cannot block the health endpoint.
     */
    suspend fun ping(): Boolean

    /** Exposes the underlying database for repositories. */
    fun database(): MongoDatabase

    override fun close()
}

/**
 * Factory used when no real database is required, for example in unit tests or
 * when `MONGODB_URI` is not set. `/health` reports the database as `degraded`
 * in that case rather than failing.
 */
object NoOpMongoConnectionFactory : MongoConnectionFactory {
    override fun create(settings: MongoSettings): MongoConnection = NoOpMongoConnection(settings)
}

private class NoOpMongoConnection(
    private val settings: MongoSettings,
) : MongoConnection {

    override val databaseName: String
        get() = settings.databaseName

    override suspend fun ping(): Boolean = false

    override fun database(): MongoDatabase =
        error("No MONGODB_URI is configured; database() must not be called")

    override fun close() = Unit
}

/**
 * Production [MongoConnectionFactory] backed by the MongoDB Kotlin coroutine
 * driver.
 */
class DriverMongoConnectionFactory(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val pingTimeoutMs: Long = DEFAULT_PING_TIMEOUT_MS,
) : MongoConnectionFactory {

    private val log = LoggerFactory.getLogger(DriverMongoConnectionFactory::class.java)

    override fun create(settings: MongoSettings): MongoConnection {
        if (!settings.isConfigured) {
            log.warn(
                "MONGODB_URI is not set; the API will start without a database connection. " +
                    "Health checks will report the database as degraded.",
            )
            return NoOpMongoConnectionFactory.create(settings)
        }
        log.info(
            "Opening MongoDB connection to database '{}' (URI redacted)",
            settings.databaseName,
        )
        return DriverMongoConnection(settings, ioDispatcher, pingTimeoutMs)
    }

    private class DriverMongoConnection(
        private val settings: MongoSettings,
        private val ioDispatcher: CoroutineDispatcher,
        private val pingTimeoutMs: Long,
    ) : MongoConnection {

        private val log = LoggerFactory.getLogger(DriverMongoConnection::class.java)

        private var client: MongoClient? = null
        private var database: MongoDatabase? = null

        override val databaseName: String
            get() = settings.databaseName

        override suspend fun ping(): Boolean = withContext(ioDispatcher) {
            try {
                withTimeout(pingTimeoutMs) {
                    database().runCommand(Document("ping", 1))
                }
                true
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                log.warn("MongoDB ping failed: {}", error::class.simpleName)
                false
            }
        }

        override fun database(): MongoDatabase {
            val existing = database
            if (existing != null) return existing
            return synchronized(this) {
                database ?: createClient().also { database = it }
            }
        }

        private fun createClient(): MongoDatabase {
            val created = MongoClient.create(settings.uri)
            client = created
            return created.getDatabase(settings.databaseName)
        }

        override fun close() {
            synchronized(this) {
                client?.close()
                client = null
                database = null
            }
            log.info("MongoDB connection closed")
        }
    }

    private companion object {
        const val DEFAULT_PING_TIMEOUT_MS = 3_000L
    }
}
