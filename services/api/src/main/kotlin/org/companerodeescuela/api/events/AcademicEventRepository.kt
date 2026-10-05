package org.companerodeescuela.api.events

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.toList
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonProperty
import org.companerodeescuela.shared.contracts.AcademicEvent
import org.companerodeescuela.shared.contracts.AcademicEventPriority
import org.companerodeescuela.shared.contracts.AcademicEventScope
import org.companerodeescuela.shared.contracts.AcademicEventSource
import org.companerodeescuela.shared.contracts.AcademicEventTarget
import org.companerodeescuela.shared.contracts.AcademicEventType

interface AcademicEventRepository {
    suspend fun listEvents(scopeIds: Set<String>? = null): List<AcademicEvent>
    suspend fun findById(id: String): AcademicEvent?
    suspend fun save(event: AcademicEvent): AcademicEvent
    suspend fun update(id: String, event: AcademicEvent): AcademicEvent?
    suspend fun delete(id: String): Boolean
}

internal data class AcademicEventDocument(
    @param:BsonId val id: String,
    @param:BsonProperty("type") val type: String,
    @param:BsonProperty("source") val source: String,
    @param:BsonProperty("sourceId") val sourceId: String,
    @param:BsonProperty("sourceDisplayName") val sourceDisplayName: String?,
    @param:BsonProperty("targetScope") val targetScope: String,
    @param:BsonProperty("targetId") val targetId: String,
    @param:BsonProperty("occurrenceId") val occurrenceId: String?,
    @param:BsonProperty("courseId") val courseId: String?,
    @param:BsonProperty("subjectName") val subjectName: String?,
    @param:BsonProperty("groupName") val groupName: String?,
    @param:BsonProperty("title") val title: String,
    @param:BsonProperty("body") val body: String?,
    @param:BsonProperty("priority") val priority: String,
    @param:BsonProperty("previousStartsAt") val previousStartsAt: String?,
    @param:BsonProperty("newStartsAt") val newStartsAt: String?,
    @param:BsonProperty("previousEndsAt") val previousEndsAt: String?,
    @param:BsonProperty("newEndsAt") val newEndsAt: String?,
    @param:BsonProperty("previousRoom") val previousRoom: String?,
    @param:BsonProperty("newRoom") val newRoom: String?,
    @param:BsonProperty("onlineUrl") val onlineUrl: String?,
    @param:BsonProperty("effectiveAtEpochSeconds") val effectiveAtEpochSeconds: Long?,
    @param:BsonProperty("createdAtEpochSeconds") val createdAtEpochSeconds: Long,
    @param:BsonProperty("expiresAtEpochSeconds") val expiresAtEpochSeconds: Long?,
) {
    fun toModel(): AcademicEvent = AcademicEvent(
        id = id,
        type = AcademicEventType.valueOf(type),
        source = AcademicEventSource.valueOf(source),
        sourceId = sourceId,
        sourceDisplayName = sourceDisplayName,
        target = AcademicEventTarget(
            scope = AcademicEventScope.valueOf(targetScope),
            id = targetId,
        ),
        occurrenceId = occurrenceId,
        courseId = courseId,
        subjectName = subjectName,
        groupName = groupName,
        title = title,
        body = body,
        priority = AcademicEventPriority.valueOf(priority),
        previousStartsAt = previousStartsAt,
        newStartsAt = newStartsAt,
        previousEndsAt = previousEndsAt,
        newEndsAt = newEndsAt,
        previousRoom = previousRoom,
        newRoom = newRoom,
        onlineUrl = onlineUrl,
        effectiveAtEpochSeconds = effectiveAtEpochSeconds,
        createdAtEpochSeconds = createdAtEpochSeconds,
        expiresAtEpochSeconds = expiresAtEpochSeconds,
    )

    companion object {
        fun fromModel(event: AcademicEvent): AcademicEventDocument = AcademicEventDocument(
            id = event.id,
            type = event.type.name,
            source = event.source.name,
            sourceId = event.sourceId,
            sourceDisplayName = event.sourceDisplayName,
            targetScope = event.target.scope.name,
            targetId = event.target.id,
            occurrenceId = event.occurrenceId,
            courseId = event.courseId,
            subjectName = event.subjectName,
            groupName = event.groupName,
            title = event.title,
            body = event.body,
            priority = event.priority.name,
            previousStartsAt = event.previousStartsAt,
            newStartsAt = event.newStartsAt,
            previousEndsAt = event.previousEndsAt,
            newEndsAt = event.newEndsAt,
            previousRoom = event.previousRoom,
            newRoom = event.newRoom,
            onlineUrl = event.onlineUrl,
            effectiveAtEpochSeconds = event.effectiveAtEpochSeconds,
            createdAtEpochSeconds = event.createdAtEpochSeconds,
            expiresAtEpochSeconds = event.expiresAtEpochSeconds,
        )
    }
}

class InMemoryAcademicEventRepository : AcademicEventRepository {
    private val storage = ConcurrentHashMap<String, AcademicEvent>()

    override suspend fun listEvents(scopeIds: Set<String>?): List<AcademicEvent> {
        val all = storage.values.sortedByDescending { it.createdAtEpochSeconds }
        if (scopeIds.isNullOrEmpty()) return all
        return all.filter { event ->
            event.target.scope == AcademicEventScope.INSTITUTION ||
                event.target.id in scopeIds ||
                event.courseId in scopeIds ||
                event.occurrenceId in scopeIds
        }
    }

    override suspend fun findById(id: String): AcademicEvent? = storage[id]

    override suspend fun save(event: AcademicEvent): AcademicEvent {
        storage[event.id] = event
        return event
    }

    override suspend fun update(id: String, event: AcademicEvent): AcademicEvent? {
        if (!storage.containsKey(id)) return null
        storage[id] = event
        return event
    }

    override suspend fun delete(id: String): Boolean = storage.remove(id) != null
}

class MongoAcademicEventRepository(
    database: MongoDatabase,
) : AcademicEventRepository {
    private val collection = database.getCollection<AcademicEventDocument>("academic_events")

    override suspend fun listEvents(scopeIds: Set<String>?): List<AcademicEvent> {
        val filter = if (scopeIds.isNullOrEmpty()) {
            Filters.empty()
        } else {
            Filters.or(
                Filters.eq("targetScope", AcademicEventScope.INSTITUTION.name),
                Filters.`in`("targetId", scopeIds),
                Filters.`in`("courseId", scopeIds),
                Filters.`in`("occurrenceId", scopeIds),
            )
        }
        return collection.find(filter)
            .sort(Sorts.descending("createdAtEpochSeconds"))
            .toList()
            .map { it.toModel() }
    }

    override suspend fun findById(id: String): AcademicEvent? {
        return collection.find(Filters.eq("_id", id)).toList().firstOrNull()?.toModel()
    }

    override suspend fun save(event: AcademicEvent): AcademicEvent {
        val doc = AcademicEventDocument.fromModel(event)
        collection.insertOne(doc)
        return event
    }

    override suspend fun update(id: String, event: AcademicEvent): AcademicEvent? {
        val doc = AcademicEventDocument.fromModel(event)
        val result = collection.replaceOne(Filters.eq("_id", id), doc)
        return if (result.matchedCount > 0) event else null
    }

    override suspend fun delete(id: String): Boolean {
        val result = collection.deleteOne(Filters.eq("_id", id))
        return result.deletedCount > 0
    }
}
