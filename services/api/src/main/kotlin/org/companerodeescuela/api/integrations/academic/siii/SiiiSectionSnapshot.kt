package org.companerodeescuela.api.integrations.academic.siii

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.security.MessageDigest
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

@Serializable
data class SiiiSectionSnapshot(
    val contract: String,
    val sectionId: String,
    val sectionCode: String,
    val status: String,
    val timezone: String,
    val term: SiiiTerm,
    val subject: SiiiSubject,
    val teachers: List<SiiiTeacher>,
    val enrollments: List<SiiiEnrollment>,
    val occurrences: List<SiiiOccurrence>,
)

@Serializable
data class SiiiTerm(
    val sourceId: String,
    val code: String,
    val startsOn: String,
    val endsOn: String,
)

@Serializable
data class SiiiSubject(
    val sourceId: String,
    val code: String,
    val name: String,
    val credits: Int,
)

@Serializable
data class SiiiTeacher(
    val sourceId: String,
    val externalId: String,
    val fullName: String,
)

@Serializable
data class SiiiEnrollment(
    val sourceId: String,
    val studentSourceId: String,
    val studentExternalId: String,
    val fullName: String,
    val status: String,
)

@Serializable
data class SiiiOccurrence(
    val sourceId: String,
    val patternSourceId: String,
    val date: String,
    val startsAt: String,
    val endsAt: String,
    val status: String,
    val room: SiiiRoom,
)

@Serializable
data class SiiiRoom(
    val sourceId: String,
    val code: String,
    val building: String,
    val campusSourceId: String,
    val campusName: String,
)

object SiiiSectionSnapshotCodec {
    const val CONTRACT = "siii.academic_section_snapshot.v1"
    private val json =
        Json {
            ignoreUnknownKeys = false
            coerceInputValues = false
            isLenient = false
        }
    private val code = Regex("[A-Z0-9][A-Z0-9_-]+")
    private val calendarDate = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")

    fun decode(
        payload: JsonObject,
        sectionId: String,
    ): SiiiSectionSnapshot =
        try {
            decodeValidated(payload, sectionId)
        } catch (_: DateTimeException) {
            throw IllegalArgumentException("Invalid snapshot date or instant")
        }

    private fun decodeValidated(
        payload: JsonObject,
        sectionId: String,
    ): SiiiSectionSnapshot {
        val credits = (payload["subject"] as? JsonObject)?.get("credits") as? JsonPrimitive
        require(credits != null && !credits.isString && credits.intOrNull != null)
        val value =
            try {
                json.decodeFromString<SiiiSectionSnapshot>(payload.toString())
            } catch (_: SerializationException) {
                throw IllegalArgumentException("Invalid institutional snapshot")
            }
        uuid(sectionId)
        require(value.contract == CONTRACT && value.sectionId == sectionId)
        uuid(value.sectionId)
        code(value.sectionCode, 32)
        require(value.status in setOf("open", "closed", "archived"))
        require(value.timezone in ZoneId.getAvailableZoneIds())
        val zone = ZoneId.of(value.timezone)
        uuid(value.term.sourceId)
        code(value.term.code, 25)
        val termStart = date(value.term.startsOn)
        val termEnd = date(value.term.endsOn)
        require(termStart <= termEnd)
        uuid(value.subject.sourceId)
        code(value.subject.code, 25)
        text(value.subject.name, 3, 160)
        require(value.subject.credits in 0..30)
        require(value.teachers.size <= 500)
        unique(value.teachers.map { it.sourceId })
        unique(value.teachers.map { it.externalId })
        value.teachers.forEach {
            uuid(it.sourceId)
            code(it.externalId, 32)
            text(it.fullName, 2, 241)
        }
        require(value.enrollments.size <= 500)
        unique(value.enrollments.map { it.sourceId })
        unique(value.enrollments.filter { it.status == "enrolled" }.map { it.studentSourceId })
        value.enrollments.forEach {
            uuid(it.sourceId)
            uuid(it.studentSourceId)
            code(it.studentExternalId, 32)
            text(it.fullName, 2, 241)
            require(it.status in setOf("enrolled", "withdrawn"))
        }
        require(
            value.enrollments.groupBy { it.studentSourceId }.values.all { rows ->
                rows.map { it.studentExternalId to it.fullName }.distinct().size ==
                    1
            },
        )
        require(
            value.enrollments.groupBy { it.studentExternalId }.values.all { rows ->
                rows.map { it.studentSourceId }.distinct().size == 1
            },
        )
        require(value.occurrences.size <= 3000)
        unique(value.occurrences.map { it.sourceId })
        unique(value.occurrences.map { it.patternSourceId to it.date })
        val normalizedOccurrences =
            value.occurrences.map {
                uuid(it.sourceId)
                uuid(it.patternSourceId)
                val onDate = date(it.date)
                require(onDate in termStart..termEnd)
                require(it.status in setOf("SCHEDULED", "CANCELLED", "ONLINE"))
                require(it.startsAt.endsWith("Z") && it.endsAt.endsWith("Z"))
                val start = instant(it.startsAt)
                val end = instant(it.endsAt)
                require(start < end)
                val localStart = start.atZone(zone)
                val localEnd = end.atZone(zone)
                require(localStart.toLocalDate() == onDate)
                require(localStart.second == 0 && localStart.nano == 0 && localEnd.second == 0 && localEnd.nano == 0)
                require(
                    (localEnd.toLocalDate() == onDate && localEnd.toLocalTime() > localStart.toLocalTime()) ||
                        (localEnd.toLocalDate() == onDate.plusDays(1) && localEnd.toLocalTime() == LocalTime.MIDNIGHT),
                )
                uuid(it.room.sourceId)
                uuid(it.room.campusSourceId)
                code(it.room.code, 32)
                text(it.room.building, 2, 120)
                text(it.room.campusName, 3, 160)
                it.copy(startsAt = start.toString(), endsAt = end.toString())
            }
        return value.copy(
            teachers = value.teachers.sortedBy { it.sourceId },
            enrollments = value.enrollments.sortedBy { it.sourceId },
            occurrences = normalizedOccurrences.sortedBy { it.sourceId },
        )
    }

    fun canonical(snapshot: SiiiSectionSnapshot): String =
        canonicalElement(json.parseToJsonElement(json.encodeToString(snapshot))).toString()

    fun digest(snapshot: SiiiSectionSnapshot): String = sha256(canonical(snapshot))

    internal fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") {
            "%02x".format(it)
        }

    internal fun uuid(value: String) {
        require(UUID.fromString(value).toString() == value)
    }

    private fun date(value: String): LocalDate {
        require(calendarDate.matches(value))
        return try {
            LocalDate.parse(value)
        } catch (_: DateTimeException) {
            throw IllegalArgumentException("Invalid snapshot date")
        }
    }

    private fun instant(value: String): Instant =
        try {
            Instant.parse(value)
        } catch (_: DateTimeException) {
            throw IllegalArgumentException("Invalid snapshot instant")
        }

    private fun code(
        value: String,
        max: Int,
    ) {
        require(value.length in 2..max && code.matches(value))
    }

    private fun text(
        value: String,
        min: Int,
        max: Int,
    ) {
        require(
            value.length in min..max && value == value.trim() && value.none(Char::isISOControl),
        )
    }

    private fun <T> unique(values: List<T>) {
        require(values.size == values.distinct().size)
    }

    private fun canonicalElement(value: JsonElement): JsonElement =
        when (value) {
            is JsonObject -> JsonObject(value.toSortedMap().mapValues { canonicalElement(it.value) })
            is JsonArray -> JsonArray(value.map(::canonicalElement))
            else -> value
        }
}
