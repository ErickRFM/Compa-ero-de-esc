package org.companerodeescuela.api.academic

import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.client.model.Sorts
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.withLock
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonProperty
import org.companerodeescuela.shared.contracts.AcademicDataSource
import org.companerodeescuela.shared.contracts.AcademicProvenance
import org.companerodeescuela.shared.contracts.BlockStatus
import org.companerodeescuela.shared.contracts.ManagedScheduleBlock
import org.companerodeescuela.shared.contracts.ScheduleBlock
import org.companerodeescuela.shared.contracts.ScheduleRecurrence
import org.companerodeescuela.shared.contracts.ScheduleShift

interface AcademicScheduleOverrideRepository {
    suspend fun listForOwner(ownerId: String): List<ManagedScheduleBlock>
    suspend fun findById(id: String): ManagedScheduleBlock?
    suspend fun save(value: ManagedScheduleBlock): ManagedScheduleBlock
    suspend fun saveScoped(value:ManagedScheduleBlock,expectedRevision:Long):ManagedScheduleBlock? = null
    suspend fun delete(id: String): Boolean
}

class InMemoryAcademicScheduleOverrideRepository : AcademicScheduleOverrideRepository {
    private val storage = ConcurrentHashMap<String, ManagedScheduleBlock>()
    private val scopeMutex=kotlinx.coroutines.sync.Mutex()

    override suspend fun listForOwner(ownerId: String): List<ManagedScheduleBlock> =
        storage.values
            .filter { it.ownerId == ownerId }
            .sortedWith(compareBy({ it.block.dayOfWeek }, { it.block.startTime }, { it.block.subjectName }))

    override suspend fun findById(id: String): ManagedScheduleBlock? = storage[id]

    override suspend fun save(value: ManagedScheduleBlock): ManagedScheduleBlock {
        storage[value.block.id] = value
        return value
    }

    override suspend fun saveScoped(value:ManagedScheduleBlock,expectedRevision:Long):ManagedScheduleBlock? = scopeMutex.withLock {
        if(!value.validScopedRevision(expectedRevision)) return@withLock null
        val current=storage[value.block.id]
        if(current!=null && current.exactScopedRetry(value,expectedRevision)) return@withLock current
        if((current==null && expectedRevision!=0L) || (current!=null && current.revision!=expectedRevision)) return@withLock null
        storage[value.block.id]=value
        value
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
    @param:BsonProperty("recurrence") val recurrence: String = ScheduleRecurrence.WEEKLY.name,
    @param:BsonProperty("seriesId") val seriesId: String? = null,
    @param:BsonProperty("effectiveDate") val effectiveDate: String? = null,
    @param:BsonProperty("source") val source: String,
    @param:BsonProperty("sourceId") val sourceId: String?,
    @param:BsonProperty("verified") val verified: Boolean,
    @param:BsonProperty("createdBy") val createdBy: String?,
    @param:BsonProperty("updatedBy") val updatedBy: String?,
    @param:BsonProperty("createdAtEpochSeconds") val createdAtEpochSeconds: Long,
    @param:BsonProperty("updatedAtEpochSeconds") val updatedAtEpochSeconds: Long,
    @param:BsonProperty("reason") val reason: String?,
    @param:BsonProperty("institutionId") val institutionId:String?=null,
    @param:BsonProperty("groupScopeRevision") val groupScopeRevision:Long?=null,
    @param:BsonProperty("revision") val revision:Long?=null,
    @param:BsonProperty("operationId") val operationId:String?=null,
    @param:BsonProperty("operationDigest") val operationDigest:String?=null,
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
            recurrence = runCatching { ScheduleRecurrence.valueOf(recurrence) }
                .getOrDefault(ScheduleRecurrence.WEEKLY),
            seriesId = seriesId,
            effectiveDate = effectiveDate,
        ),
        reason = reason,institutionId=institutionId,groupScopeRevision=groupScopeRevision ?: -1,revision=revision ?: 0,operationId=operationId,operationDigest=operationDigest,
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
                recurrence = block.recurrence.name,
                seriesId = block.seriesId,
                effectiveDate = block.effectiveDate,
                source = provenance.source.name,
                sourceId = provenance.sourceId,
                verified = provenance.verified,
                createdBy = provenance.createdBy,
                updatedBy = provenance.updatedBy,
                createdAtEpochSeconds = provenance.createdAtEpochSeconds,
                updatedAtEpochSeconds = provenance.updatedAtEpochSeconds,
                reason = value.reason,institutionId=value.institutionId,groupScopeRevision=value.groupScopeRevision,revision=value.revision,operationId=value.operationId,operationDigest=value.operationDigest,
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

    override suspend fun saveScoped(value:ManagedScheduleBlock,expectedRevision:Long):ManagedScheduleBlock? {
        if(!value.validScopedRevision(expectedRevision)) return null
        val current=findById(value.block.id)
        if(current!=null && current.exactScopedRetry(value,expectedRevision)) return current
        if(current==null) {
            if(expectedRevision!=0L) return null
            try { collection.insertOne(AcademicScheduleOverrideDocument.fromModel(value));return value }
            catch(error:com.mongodb.MongoWriteException) { if(error.error.code!=11000) throw error }
        } else if(current.revision==expectedRevision) {
            val filter=Filters.and(Filters.eq("_id",value.block.id),Filters.eq("revision",expectedRevision))
            val changed=collection.findOneAndReplace(filter,AcademicScheduleOverrideDocument.fromModel(value),
                com.mongodb.client.model.FindOneAndReplaceOptions().returnDocument(com.mongodb.client.model.ReturnDocument.AFTER))
            if(changed!=null) return changed.toModel()
        }
        return findById(value.block.id)?.takeIf { it.exactScopedRetry(value,expectedRevision) }
    }

    override suspend fun delete(id: String): Boolean =
        collection.deleteOne(Filters.eq("_id", id)).deletedCount > 0
}

private fun ManagedScheduleBlock.validScopedRevision(expectedRevision:Long)=expectedRevision>=0 && expectedRevision<Long.MAX_VALUE && revision==expectedRevision+1 &&
    !institutionId.isNullOrBlank() && groupScopeRevision>=0 && !operationId.isNullOrBlank() && operationDigest?.matches(Regex("[a-f0-9]{64}"))==true
private fun ManagedScheduleBlock.exactScopedRetry(request:ManagedScheduleBlock,expectedRevision:Long):Boolean {
    fun ManagedScheduleBlock.normalized()=copy(block=block.copy(provenance=block.provenance.copy(createdAtEpochSeconds=0,updatedAtEpochSeconds=0)))
    return revision==expectedRevision+1 && normalized()==request.normalized()
}
