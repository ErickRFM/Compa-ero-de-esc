package org.companerodeescuela.shared.contracts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ScheduleShift {
    @SerialName("morning") MORNING,
    @SerialName("afternoon") AFTERNOON,
    @SerialName("mixed") MIXED,
}

@Serializable
enum class BlockStatus {
    @SerialName("scheduled") SCHEDULED,
    @SerialName("confirmed") CONFIRMED,
    @SerialName("cancelled") CANCELLED,
    @SerialName("rescheduled") RESCHEDULED,
    @SerialName("room_changed") ROOM_CHANGED,
    @SerialName("extra") EXTRA,
    @SerialName("pending") PENDING,
}

@Serializable
data class ScheduleBlock(
    val id: String,
    val dayOfWeek: String,
    val startTime: String,
    val endTime: String,
    val subjectId: String? = null,
    val subjectName: String,
    val teacherId: String? = null,
    val teacherName: String? = null,
    val room: String? = null,
    val groupId: String? = null,
    val groupName: String? = null,
    val provenance: AcademicProvenance,
    val status: BlockStatus = BlockStatus.SCHEDULED,
    val shift: ScheduleShift = ScheduleShiftRules.forBlock(startTime, endTime),
    val isContraturno: Boolean = false,
)

@Serializable
data class AcademicSchedule(
    val ownerId: String,
    val blocks: List<ScheduleBlock>,
    val regularShift: ScheduleShift = ScheduleShiftRules.forSchedule(blocks),
)

/**
 * Canonical turn boundaries used by backend and Android.
 *
 * Morning: 07:00 <= time < 14:00
 * Afternoon: 14:00 <= time <= 20:00
 * A block crossing 14:00, or a schedule containing both windows, is MIXED.
 */
object ScheduleShiftRules {
    const val DAY_START = "07:00"
    const val AFTERNOON_START = "14:00"
    const val DAY_END = "20:00"

    fun forBlock(startTime: String, endTime: String): ScheduleShift {
        val start = normalize(startTime)
        val end = normalize(endTime)
        return when {
            start < AFTERNOON_START && end <= AFTERNOON_START -> ScheduleShift.MORNING
            start >= AFTERNOON_START -> ScheduleShift.AFTERNOON
            else -> ScheduleShift.MIXED
        }
    }

    fun forSchedule(blocks: List<ScheduleBlock>): ScheduleShift {
        if (blocks.isEmpty()) return ScheduleShift.MIXED
        val shifts = blocks.map { forBlock(it.startTime, it.endTime) }.toSet()
        return when {
            shifts == setOf(ScheduleShift.MORNING) -> ScheduleShift.MORNING
            shifts == setOf(ScheduleShift.AFTERNOON) -> ScheduleShift.AFTERNOON
            else -> ScheduleShift.MIXED
        }
    }

    fun isInsideAcademicDay(startTime: String, endTime: String): Boolean {
        val start = normalize(startTime)
        val end = normalize(endTime)
        return start >= DAY_START && end <= DAY_END && start < end
    }

    private fun normalize(raw: String): String {
        val parts = raw.trim().split(":")
        if (parts.size < 2) return raw.trim()
        val hour = parts[0].toIntOrNull() ?: return raw.trim()
        val minute = parts[1].toIntOrNull() ?: return raw.trim()
        return "%02d:%02d".format(hour, minute)
    }
}
