package org.companerodeescuela.shared.contracts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json

class AcademicEventContractsTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun `event types keep stable wire names`() {
        assertEquals(
            "\"class_cancelled\"",
            json.encodeToString(AcademicEventType.serializer(), AcademicEventType.CLASS_CANCELLED),
        )
        assertEquals(
            "\"room_changed\"",
            json.encodeToString(AcademicEventType.serializer(), AcademicEventType.ROOM_CHANGED),
        )
        assertEquals(
            "\"conference\"",
            json.encodeToString(AcademicEventType.serializer(), AcademicEventType.CONFERENCE),
        )
    }

    @Test
    fun `academic event round trips with occurrence metadata`() {
        val event = AcademicEvent(
            id = "event-1",
            type = AcademicEventType.CLASS_RESCHEDULED,
            source = AcademicEventSource.TEACHER,
            sourceId = "teacher-1",
            sourceDisplayName = "Mtra. Elena",
            target = AcademicEventTarget(
                scope = AcademicEventScope.CLASS_OCCURRENCE,
                id = "occurrence-1",
            ),
            occurrenceId = "occurrence-1",
            courseId = "course-1",
            subjectName = "Programación móvil",
            groupName = "4A",
            title = "Clase reprogramada",
            previousStartsAt = "10:00",
            newStartsAt = "12:00",
            createdAtEpochSeconds = 1_800_000_000L,
        )

        assertEquals(event, json.decodeFromString<AcademicEvent>(json.encodeToString(event)))
    }
}
