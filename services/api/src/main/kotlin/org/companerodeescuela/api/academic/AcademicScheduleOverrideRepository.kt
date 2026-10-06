package org.companerodeescuela.api.academic

import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.client.model.Sorts
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.toList
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonProperty
import org.companerodeescuela.shared.contracts.AcademicDataSource
import org.companerodeescuela.shared.contracts.AcademicProvenance
import org.companerodeescuela.shared.contracts.BlockStatus
import org.companerodeescuela.shared.contracts.ManagedScheduleBlock
import org.companerodeescuela.shared.contracts.ScheduleBlock
import org.companerodeescuela.shared.contracts.ScheduleShift

interface AcademicScheduleOverrideRepository {
    suspend fun listForOwner(ownerId: String): List<ManagedScheduleBlock>
    suspend fun findById(id: String): ManagedScheduleBlock?
    suspend fun save(value: ManagedScheduleBlock): ManagedScheduleBlock
    suspend fun delete(id: String): Boolean
}

class InMemoryAcademicScheduleOverrideRepository : AcademicScheduleOverrideRepository {
    private val storage = ConcurrentHashMap<String, ManagedScheduleBlock>()

    override suspend fun listForOwner(ownerId: String): List<ManagedScheduleBlock> =
        storage.values
            .filter { it.ownerId == ownerId }
            .sortedWith(compareBy({ it.block.dayOfWeek }, { it.block.startTime }, { it.block.subjectName }))

    override suspend fun findById(id: String): ManagedScheduleBlock? = storage[id]

    override suspend fun save(value: ManagedScheduleBlock): ManagedScheduleBlock {
        storage[value.block.id] = value
        return value
    }

    override suspend fun delete(id: String): Boolean = storage.remove(id) != null
}

internal data class AcademicScheduleOverrideDocument(
    @param:BsonId val id: String,
    @param:BsonProperty("ownerId") val ownerId: String,
    @param:BsonProperty("dayOfWeek") val dayOfWeek: String,
    @param:BsonProperty("startTime") val startTime: String,
    @param:BsonProperty("endTime") val endTime: String,
    @param:BsonProperty("subjectId") val subjectId: String?,
    @param:BsonProperty("subjectName") val subjectName: String,
    @param:BsonProperty("teacherId") val teacherId: String?,
    @param:BsonProperty("teacherName") val teacherName: String?,
    @param:BsonProperty("room") val room: String?,
    @param:BsonProperty("groupId") val groupId: String?,
    @param:BsonProperty("groupName") val groupName: String?,
    @param:BsonProperty("status") val status: String,
    @param:BsonProperty("shift") val shift: String,
    @param:BsonProperty("isContraturno") val isContraturno: Boolean,
    @param:BsonProperty("source") val source: String,
    @param:BsonProperty("sourceId") val sourceId: String?,
    @param:BsonProperty("verified") val verified: Boolean,
    @param:BsonProperty("createdBy") val createdBy: String?,
    @param:BsonProperty("updatedBy") val updatedBy: String?,
    @param:BsonProperty("createdAtEpochSeconds") val createdAtEpochSeconds: Long,
    @param:BsonProperty("updatedAtEpochSeconds") val updatedAtEpochSeconds: Long,
    @param:BsonProperty("reason") val reason: String?,
) {
    fun toModel(): ManagedScheduleBlock = ManagedScheduleBlock(
        ownerId = ownerId,
        block = ScheduleBlock(
            id = id,
            dayOfWeek = dayOfWeek,
            startTime = startTime,
            endTime = endTime,
            subjectId = subjectId,
            subjectName = subjectName,
            teacherId = teacherId,
            teacherName = teacherName,
            room = room,
            groupId = groupId,
            groupName = groupName,
            provenance = AcademicProvenance(
                source = AcademicDataSource.valueOf(source),
                sourceId = sourceId,
                verified = verified,
                createdBy = createdBy,
                updatedBy = updatedBy,
                createdAtEpochSeconds = createdAtEpochSeconds,
                updatedAtEpochSeconds = updatedAtEpochSeconds,
            ),
            status = BlockStatus.valueOf(status),
            shift = ScheduleShift.valueOf(shift),
            isContraturno = isContraturno,
        ),
        reason = reason,
    )

    companion object {
        fun fromModel(value: ManagedScheduleBlock): AcademicScheduleOverrideDocument {
            val block = value.block
            val provenance = block.provenance
            return AcademicScheduleOverrideDocument(
                id = block.id,
                ownerId = value.ownerId,
                dayOfWeek = block.dayOfWeek,
                startTime = block.startTime,
                endTime = block.endTime,
                subjectId = block.subjectId,
                subjectName = block.subjectName,
                teacherId = block.teacherId,
                teacherName = block.teacherName,
                room = block.room,
                groupId = block.groupId,
                groupName = block.groupName,
                status = block.status.name,
                shift = block.shift.name,
                isContraturno = block.isContraturno,
                source = provenance.source.name,
                sourceId = provenance.sourceId,
                verified = provenance.verified,
                createdBy = provenance.createdBy,
                updatedBy = provenance.updatedBy,
                createdAtEpochSeconds = provenance.createdAtEpochSeconds,
                updatedAtEpochSeconds = provenance.updatedAtEpochSeconds,
                reason = value.reason,
            )
        }
    }
}

class MongoAcademicScheduleOverrideRepository(
    database: MongoDatabase,
) : AcademicScheduleOverrideRepository {
    private val collection =
        database.getCollection<AcademicScheduleOverrideDocument>("academic_schedule_overrides")

    override suspend fun listForOwner(ownerId: String): List<ManagedScheduleBlock> =
        collection.find(Filters.eq("ownerId", ownerId))
            .sort(Sorts.ascending("dayOfWeek", "startTime"))
            .toList()
            .map(AcademicScheduleOverrideDocument::toModel)

    override suspend fun findById(id: String): ManagedScheduleBlock? =
        collection.find(Filters.eq("_id", id)).toList().firstOrNull()?.toModel()

    override suspend fun save(value: ManagedScheduleBlock): ManagedScheduleBlock {
        collection.replaceOne(
            Filters.eq("_id", value.block.id),
            AcademicScheduleOverrideDocument.fromModel(value),
            ReplaceOptions().upsert(true),
        )
        return value
    }

    override suspend fun delete(id: String): Boolean =
        collection.deleteOne(Filters.eq("_id", id)).deletedCount > 0
}
