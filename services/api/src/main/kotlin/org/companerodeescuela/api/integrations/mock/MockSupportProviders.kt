package org.companerodeescuela.api.integrations.mock

import java.time.Instant
import java.time.LocalDate
import org.companerodeescuela.api.integrations.MockIntegrationProvider
import org.companerodeescuela.api.integrations.events.EventsProvider
import org.companerodeescuela.api.integrations.events.ExternalEvent
import org.companerodeescuela.api.integrations.library.ExternalLibraryItem
import org.companerodeescuela.api.integrations.library.ExternalLoan
import org.companerodeescuela.api.integrations.library.LibraryProvider
import org.companerodeescuela.api.integrations.sync.SyncOutcome
import org.companerodeescuela.api.integrations.sync.SyncProvider
import java.time.Clock

/** Development-only campus events source. */
class MockEventsProvider(private val clock: Clock = Clock.systemUTC()) :
    EventsProvider,
    MockIntegrationProvider {
    override val id: String = "mock-events"
    override val displayName: String = "Mock events source (development only)"

    override suspend fun listEvents(from: LocalDate, to: LocalDate): List<ExternalEvent> {
        val today = LocalDate.now(clock)
        return listOf(
            ExternalEvent(
                externalId = "EV-1",
                title = "Reunión de padres",
                description = "Presentación del periodo.",
                startsOn = today.plusDays(3),
                endsOn = today.plusDays(3),
                startTime = java.time.LocalTime.of(18, 0),
                endTime = java.time.LocalTime.of(19, 30),
                location = "Auditorio Central",
                audience = setOf("ALL"),
            ),
        ).filter { it.startsOn >= from && it.startsOn <= to }
    }
}

/** Development-only library catalogue. */
class MockLibraryProvider : LibraryProvider, MockIntegrationProvider {
    override val id: String = "mock-library"
    override val displayName: String = "Mock library (development only)"

    override suspend fun search(query: String, limit: Int): List<ExternalLibraryItem> {
        val effectiveLimit = limit.coerceIn(1, LibraryProvider.MAX_LIMIT)
        val needle = query.trim().lowercase()
        return ITEMS
            .filter { needle.isEmpty() || it.title.lowercase().contains(needle) }
            .take(effectiveLimit)
    }

    override suspend fun listLoans(borrowerExternalId: String): List<ExternalLoan> = LOANS

    private companion object {
        val ITEMS = listOf(
            ExternalLibraryItem("L-1", "Clean Architecture", "Robert C. Martin", "978-0134494166"),
            ExternalLibraryItem("L-2", "Designing Data-Intensive Applications", "Martin Kleppmann", "978-1449373320"),
            ExternalLibraryItem("L-3", "Cálculo diferencial", "James Stewart", "978-0534490316"),
        )

        val LOANS = listOf(
            ExternalLoan(
                itemExternalId = "L-1",
                title = "Clean Architecture",
                borrowedOn = LocalDate.of(2026, 1, 20),
                dueOn = LocalDate.of(2026, 2, 3),
                renewalsUsed = 1,
            ),
        )
    }
}

/** Development-only sync provider for the academic dataset. */
class MockSyncProvider(private val clock: Clock = Clock.systemUTC()) :
    SyncProvider,
    MockIntegrationProvider {
    override val id: String = "mock-sync"
    override val displayName: String = "Mock sync (development only)"
    override val dataset: String = "academic-load"

    override suspend fun sync(externalId: String, since: Instant?): SyncOutcome {
        val startedAt = clock.instant()
        val items = if (MockFixtures.STUDENTS.containsKey(externalId)) {
            MockFixtures.ENROLLMENTS.size
        } else {
            0
        }
        return SyncOutcome(
            providerId = id,
            startedAt = startedAt,
            finishedAt = clock.instant(),
            itemsRead = items,
            succeeded = true,
        )
    }
}
