package org.companerodeescuela.core.database

import java.time.Clock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.companerodeescuela.shared.contracts.AcademicLoadResponse

data class CachedAcademicLoad(
    val value: AcademicLoadResponse,
    val updatedAtEpochSeconds: Long,
)

interface AcademicSnapshotCache {
    suspend fun read(): CachedAcademicLoad?
    suspend fun write(value: AcademicLoadResponse)
    suspend fun clear()
}

internal class RoomAcademicSnapshotCache(
    private val dao: AcademicSnapshotDao,
    private val clock: Clock = Clock.systemUTC(),
) : AcademicSnapshotCache {

    override suspend fun read(): CachedAcademicLoad? =
        dao.find(CACHE_KEY)?.let { entity ->
            CachedAcademicLoad(
                value = AcademicSnapshotCodec.decode(entity.payloadJson),
                updatedAtEpochSeconds = entity.updatedAtEpochSeconds,
            )
        }

    override suspend fun write(value: AcademicLoadResponse) {
        dao.upsert(
            AcademicSnapshotEntity(
                cacheKey = CACHE_KEY,
                ownerId = value.student.id,
                payloadJson = AcademicSnapshotCodec.encode(value),
                updatedAtEpochSeconds = clock.instant().epochSecond,
            ),
        )
    }

    override suspend fun clear() {
        dao.delete(CACHE_KEY)
    }

    private companion object {
        const val CACHE_KEY = "current-user"
    }
}

internal object AcademicSnapshotCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun encode(value: AcademicLoadResponse): String = json.encodeToString(value)

    fun decode(raw: String): AcademicLoadResponse = json.decodeFromString(raw)
}

object AcademicSnapshotCacheFactory {
    fun create(database: CompaneroDatabase): AcademicSnapshotCache =
        RoomAcademicSnapshotCache(database.academicSnapshotDao())
}
