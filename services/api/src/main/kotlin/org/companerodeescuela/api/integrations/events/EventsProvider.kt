package org.companerodeescuela.api.integrations.events

import java.time.LocalDate
import java.time.LocalTime
import org.companerodeescuela.api.integrations.IntegrationProvider

/** An institutional event as published by the campus calendar system. */
data class ExternalEvent(
    val externalId: String,
    val title: String,
    val description: String? = null,
    val startsOn: LocalDate,
    val endsOn: LocalDate? = null,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val location: String? = null,
    val audience: Set<String> = emptySet(),
)

/**
 * Adapter for the campus events and announcements system.
 *
 * Kept separate from announcements the platform itself owns: institutional
 * notices are read-only here, and any message created in the app is stored
 * locally in the platform's own database.
 */
interface EventsProvider : IntegrationProvider {
    suspend fun listEvents(from: LocalDate, to: LocalDate): List<ExternalEvent>
}
