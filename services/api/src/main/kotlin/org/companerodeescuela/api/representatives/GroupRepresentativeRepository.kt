package org.companerodeescuela.api.representatives

import com.mongodb.client.model.Filters
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.Sorts
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.toList
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonProperty
import org.companerodeescuela.shared.contracts.GroupRepresentativeAssignment
import org.companerodeescuela.shared.contracts.RepresentativePosition
import org.companerodeescuela.shared.contracts.RepresentativeStatus

interface GroupRepresentativeRepository {
    suspend fun findById(id: String): GroupRepresentativeAssignment?
    suspend fun listByGroup(groupId: String): List<GroupRepresentativeAssignment>
    suspend fun listByStudent(studentUserId: String): List<GroupRepresentativeAssignment>
    suspend fun findActivePosition(groupId: String, position: RepresentativePosition): GroupRepresentativeAssignment?
    suspend fun save(assignment: GroupRepresentativeAssignment): GroupRepresentativeAssignment
    suspend fun update(assignment: GroupRepresentativeAssignment): GroupRepresentativeAssignment?
}

internal data class GroupRepresentativeDocument(
    @param:BsonId val id: String,
    @param:BsonProperty("institutionId") val institutionId: String?,
    @param:BsonProperty("academicGroupId") val academicGroupId: String,
    @param:BsonProperty("academicGroupName") val academicGroupName: String,
    @param:BsonProperty("academicTermId") val academicTermId: String?,
    @param:BsonProperty("studentUserId") val studentUserId: String,
    @param:BsonProperty("studentDisplayName") val studentDisplayName: String?,
    @param:BsonProperty("position") val position: String,
    @param:BsonProperty("status") val status: String,
    @param:BsonProperty("appointedBy") val appointedBy: String,
    @param:BsonProperty("appointedAtEpochSeconds") val appointedAtEpochSeconds: Long,
    @param:BsonProperty("acceptedAtEpochSeconds") val acceptedAtEpochSeconds: Long?,
    @param:BsonProperty("expiresAtEpochSeconds") val expiresAtEpochSeconds: Long?,
    @param:BsonProperty("revokedAtEpochSeconds") val revokedAtEpochSeconds: Long?,
    @param:BsonProperty("revokedBy") val revokedBy: String?,
    @param:BsonProperty("version") val version: Long,
) {
    fun toModel(): GroupRepresentativeAssignment = GroupRepresentativeAssignment(
        id = id,
        institutionId = institutionId,
        academicGroupId = academicGroupId,
        academicGroupName = academicGroupName,
        academicTermId = academicTermId,
        studentUserId = studentUserId,
        studentDisplayName = studentDisplayName,
        position = RepresentativePosition.valueOf(position),
        status = RepresentativeStatus.valueOf(status),
        appointedBy = appointedBy,
        appointedAtEpochSeconds = appointedAtEpochSeconds,
        acceptedAtEpochSeconds = acceptedAtEpochSeconds,
        expiresAtEpochSeconds = expiresAtEpochSeconds,
        revokedAtEpochSeconds = revokedAtEpochSeconds,
        revokedBy = revokedBy,
        version = version,
    )

    companion object {
        fun fromModel(model: GroupRepresentativeAssignment): GroupRepresentativeDocument =
            GroupRepresentativeDocument(
                id = model.id,
                institutionId = model.institutionId,
                academicGroupId = model.academicGroupId,
                academicGroupName = model.academicGroupName,
                academicTermId = model.academicTermId,
                studentUserId = model.studentUserId,
                studentDisplayName = model.studentDisplayName,
                position = model.position.name,
                status = model.status.name,
                appointedBy = model.appointedBy,
                appointedAtEpochSeconds = model.appointedAtEpochSeconds,
                acceptedAtEpochSeconds = model.acceptedAtEpochSeconds,
                expiresAtEpochSeconds = model.expiresAtEpochSeconds,
                revokedAtEpochSeconds = model.revokedAtEpochSeconds,
                revokedBy = model.revokedBy,
                version = model.version,
            )
    }
}

class InMemoryGroupRepresentativeRepository : GroupRepresentativeRepository {
    private val storage = ConcurrentHashMap<String, GroupRepresentativeAssignment>()

    override suspend fun findById(id: String): GroupRepresentativeAssignment? = storage[id]

    override suspend fun listByGroup(groupId: String): List<GroupRepresentativeAssignment> =
        storage.values.filter { it.academicGroupId == groupId }
            .sortedByDescending { it.appointedAtEpochSeconds }

    override suspend fun listByStudent(studentUserId: String): List<GroupRepresentativeAssignment> =
        storage.values.filter { it.studentUserId == studentUserId }
            .sortedByDescending { it.appointedAtEpochSeconds }

    override suspend fun findActivePosition(
        groupId: String,
        position: RepresentativePosition,
    ): GroupRepresentativeAssignment? =
        storage.values.firstOrNull {
            it.academicGroupId == groupId &&
                it.position == position &&
                (it.status == RepresentativeStatus.ACTIVE || it.status == RepresentativeStatus.PENDING)
        }

    override suspend fun save(assignment: GroupRepresentativeAssignment): GroupRepresentativeAssignment {
        storage[assignment.id] = assignment
        return assignment
    }

    override suspend fun update(assignment: GroupRepresentativeAssignment): GroupRepresentativeAssignment? {
        val existing = storage[assignment.id] ?: return null
        if (existing.version != assignment.version - 1) return null
        storage[assignment.id] = assignment
        return assignment
    }
}

class MongoGroupRepresentativeRepository(
    database: MongoDatabase,
) : GroupRepresentativeRepository {
    private val collection = database.getCollection<GroupRepresentativeDocument>("group_representatives")

    suspend fun ensureIndexes() {
        collection.createIndex(
            Indexes.compoundIndex(
                Indexes.ascending("academicGroupId"),
                Indexes.ascending("position"),
                Indexes.ascending("status"),
            ),
        )
        collection.createIndex(Indexes.ascending("studentUserId"))
    }

    override suspend fun findById(id: String): GroupRepresentativeAssignment? =
        collection.find(Filters.eq("_id", id)).toList().firstOrNull()?.toModel()

    override suspend fun listByGroup(groupId: String): List<GroupRepresentativeAssignment> =
        collection.find(Filters.eq("academicGroupId", groupId))
            .sort(Sorts.descending("appointedAtEpochSeconds"))
            .toList()
            .map { it.toModel() }

    override suspend fun listByStudent(studentUserId: String): List<GroupRepresentativeAssignment> =
        collection.find(Filters.eq("studentUserId", studentUserId))
            .sort(Sorts.descending("appointedAtEpochSeconds"))
            .toList()
            .map { it.toModel() }

    override suspend fun findActivePosition(
        groupId: String,
        position: RepresentativePosition,
    ): GroupRepresentativeAssignment? =
        collection.find(
            Filters.and(
                Filters.eq("academicGroupId", groupId),
                Filters.eq("position", position.name),
                Filters.`in`("status", listOf(RepresentativeStatus.ACTIVE.name, RepresentativeStatus.PENDING.name)),
            ),
        ).toList().firstOrNull()?.toModel()

    override suspend fun save(assignment: GroupRepresentativeAssignment): GroupRepresentativeAssignment {
        collection.insertOne(GroupRepresentativeDocument.fromModel(assignment))
        return assignment
    }

    override suspend fun update(assignment: GroupRepresentativeAssignment): GroupRepresentativeAssignment? {
        val filter = Filters.and(
            Filters.eq("_id", assignment.id),
            Filters.eq("version", assignment.version - 1),
        )
        val result = collection.replaceOne(filter, GroupRepresentativeDocument.fromModel(assignment))
        return if (result.matchedCount > 0) assignment else null
    }
}
