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
    suspend fun read(ownerId: String): CachedAcademicLoad?
    suspend fun write(value: AcademicLoadResponse)
    suspend fun clear(ownerId: String)
}

internal class RoomAcademicSnapshotCache(
    private val dao: AcademicSnapshotDao,
    private val clock: Clock = Clock.systemUTC(),
) : AcademicSnapshotCache {

    override suspend fun read(ownerId: String): CachedAcademicLoad? {
        require(ownerId.isNotBlank()) { "ownerId must not be blank" }
        return dao.find(ownerId)?.let { entity ->
            val decoded = AcademicSnapshotCodec.decode(entity.payloadJson)
            if (decoded.student.id != ownerId || decoded.schedule.ownerId != ownerId) {
                dao.delete(ownerId)
                return null
            }
            CachedAcademicLoad(
                value = decoded,
                updatedAtEpochSeconds = entity.updatedAtEpochSeconds,
            )
        }
    }

    override suspend fun write(value: AcademicLoadResponse) {
        require(value.student.id.isNotBlank()) { "academic snapshot owner must not be blank" }
        require(value.schedule.ownerId == value.student.id) {
            "academic snapshot owner mismatch"
        }
        dao.upsert(
            AcademicSnapshotEntity(
                cacheKey = value.student.id,
                ownerId = value.student.id,
                payloadJson = AcademicSnapshotCodec.encode(value),
                updatedAtEpochSeconds = clock.instant().epochSecond,
            ),
        )
    }

    override suspend fun clear(ownerId: String) {
        if (ownerId.isNotBlank()) {
            dao.delete(ownerId)
        }
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
