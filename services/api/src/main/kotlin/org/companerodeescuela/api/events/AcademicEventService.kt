package org.companerodeescuela.api.events

import java.time.Instant
import java.util.UUID
import org.companerodeescuela.shared.contracts.AcademicEvent
import org.companerodeescuela.shared.contracts.AcademicEventFeedResponse

class AcademicEventService(
    private val repository: AcademicEventRepository,
) {
    suspend fun listEvents(scopeIds: Set<String>? = null): AcademicEventFeedResponse {
        val events = repository.listEvents(scopeIds)
        return AcademicEventFeedResponse(
            events = events,
            generatedAtEpochSeconds = Instant.now().epochSecond,
        )
    }

    suspend fun getEvent(id: String): AcademicEvent? {
        return repository.findById(id)
    }

    suspend fun createEvent(
        event: AcademicEvent,
    ): AcademicEvent {
        val finalEvent = if (event.id.isBlank()) {
            event.copy(
                id = "EVT-" + UUID.randomUUID().toString().take(8),
                createdAtEpochSeconds = Instant.now().epochSecond,
            )
        } else {
            event.copy(createdAtEpochSeconds = Instant.now().epochSecond)
        }
        return repository.save(finalEvent)
    }

    suspend fun updateEvent(
        id: String,
        event: AcademicEvent,
    ): AcademicEvent? {
        return repository.update(id, event.copy(id = id))
    }

    suspend fun deleteEvent(id: String): Boolean {
        return repository.delete(id)
    }
}
