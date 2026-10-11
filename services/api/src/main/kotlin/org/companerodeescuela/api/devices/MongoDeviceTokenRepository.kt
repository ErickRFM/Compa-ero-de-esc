package org.companerodeescuela.api.devices

import com.mongodb.MongoWriteException
import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.gt
import com.mongodb.client.model.Filters.`in`
import com.mongodb.client.model.Filters.ne
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import java.security.MessageDigest
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bson.Document
import org.companerodeescuela.api.errors.ApiException

/** Mongo keeps durable FCM destinations; the raw token is never logged or exposed by routes. */
class MongoDeviceTokenRepository(
    database: MongoDatabase,
    private val clock: Clock = Clock.systemUTC(),
) : DeviceTokenRepository {
    private val collection = database.getCollection<Document>("device_tokens")
    private val indexMutex = Mutex()
    private var indexesReady = false

    override suspend fun register(userId: String, token: String, platform: String, model: String?, deviceId: String?) {
        val device = validateDevice(userId, token, platform, model, deviceId)
        ensureIndexes()
        val now = clock.instant()
        val document = Document("_id", device.key)
            .append("ownerId", userId)
            .append("deviceId", device.id)
            .append("platform", platform)
            .append("model", model)
            .append("token", token)
            .append("tokenHash", device.hash)
            .append("lastSeenAt", Date.from(now))
            .append("expiresAt", Date.from(now.plus(90, ChronoUnit.DAYS)))
        // A device's rotated token must not remain subscribed to its former account.
        // Unique tokenHash protects concurrent transfers across API instances.
        collection.deleteMany(and(eq("tokenHash", device.hash), ne("_id", device.key)))
        try {
            collection.replaceOne(eq("_id", device.key), document, ReplaceOptions().upsert(true))
        } catch (error: MongoWriteException) {
            if (error.error.code != 11000) throw error
            throw ApiException.Conflict("Device token was registered concurrently")
        }
    }

    override suspend fun unregister(userId: String, token: String): Boolean {
        if (userId.isBlank() || token.isBlank()) return false
        ensureIndexes()
        return collection.deleteMany(and(eq("ownerId", userId), eq("tokenHash", tokenDigest(token))))
            .deletedCount > 0
    }

    override suspend fun listTokens(userIds: Set<String>): List<String> {
        if (userIds.isEmpty()) return emptyList()
        if (userIds.size > 1000 || userIds.any { it.isBlank() })
            throw ApiException.Validation("Invalid notification recipient batch")
        ensureIndexes()
        return collection.find(and(`in`("ownerId", userIds.toList()), gt("expiresAt", Date.from(clock.instant()))))
            .toList().mapNotNull { it.getString("token") }.distinct()
    }

    override suspend fun deactivateInvalid(token: String): Boolean {
        if (token.isBlank()) return false
        ensureIndexes()
        return collection.deleteMany(eq("tokenHash", tokenDigest(token))).deletedCount > 0
    }

    private suspend fun ensureIndexes() = indexMutex.withLock {
        if (indexesReady) return@withLock
        collection.createIndex(Indexes.ascending("tokenHash"), IndexOptions().unique(true).name("ux_fcm_token_hash"))
        collection.createIndex(Indexes.ascending("ownerId"), IndexOptions().name("ix_fcm_owner"))
        collection.createIndex(Indexes.ascending("expiresAt"),
            IndexOptions().name("ttl_fcm_expiry").expireAfter(0L, TimeUnit.SECONDS))
        indexesReady = true
    }
}

/** Local/test behavior matches Mongo ownership, device rotation and revocation. */
class InMemoryDeviceTokenRepository : DeviceTokenRepository {
    private val mutex = Mutex()
    private val devices = linkedMapOf<String, DeviceEntry>()
    override suspend fun register(userId: String, token: String, platform: String, model: String?, deviceId: String?) {
        val device = validateDevice(userId, token, platform, model, deviceId)
        mutex.withLock {
            devices.entries.removeAll { it.value.hash == device.hash && it.key != device.key }
            devices[device.key] = DeviceEntry(userId, token, device.hash)
        }
    }
    override suspend fun unregister(userId: String, token: String): Boolean = mutex.withLock {
        val hash = tokenDigest(token)
        val before = devices.size
        devices.entries.removeAll { it.value.owner == userId && it.value.hash == hash }
        before != devices.size
    }
    override suspend fun listTokens(userIds: Set<String>): List<String> = mutex.withLock {
        devices.values.filter { it.owner in userIds }.map { it.token }.distinct()
    }
    override suspend fun deactivateInvalid(token: String): Boolean = mutex.withLock {
        val hash = tokenDigest(token)
        val before = devices.size
        devices.entries.removeAll { it.value.hash == hash }
        before != devices.size
    }
    private data class DeviceEntry(val owner: String, val token: String, val hash: String)
}

private data class DeviceIdentity(val key: String, val id: String, val hash: String)

private fun validateDevice(userId: String, token: String, platform: String, model: String?, deviceId: String?): DeviceIdentity {
    if (userId.isBlank() || userId.length > 512 || userId.any(Char::isISOControl))
        throw ApiException.Validation("Invalid device owner")
    if (token.length !in 16..4096 || token.any { it.isISOControl() || it.isWhitespace() })
        throw ApiException.Validation("Invalid FCM token")
    if (platform !in setOf("android", "ios"))
        throw ApiException.Validation("Unsupported device platform")
    if (model != null && (model.length > 128 || model.any(Char::isISOControl)))
        throw ApiException.Validation("Invalid device model")
    if (deviceId != null && (deviceId.isBlank() || deviceId.length > 128 || deviceId.any { !it.isLetterOrDigit() && it !in setOf('-', '_', '.') }))
        throw ApiException.Validation("Invalid device ID")
    val hash = tokenDigest(token)
    // Old clients lacking an installation ID cannot rotate by device; they can unregister by token.
    val id = deviceId ?: "token-${hash.take(32)}"
    return DeviceIdentity("${userId.length}:$userId:$id", id, hash)
}

private fun tokenDigest(token: String): String =
    MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
