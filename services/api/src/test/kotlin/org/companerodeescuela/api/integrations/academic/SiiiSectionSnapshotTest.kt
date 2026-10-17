package org.companerodeescuela.api.integrations.academic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.companerodeescuela.api.integrations.academic.siii.SiiiSectionSnapshotCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

internal const val SECTION_ID = "00000000-0000-4000-8000-000000000001"

internal fun sectionPayload(): JsonObject =
    Json.parseToJsonElement(
        """
        {
          "contract":"siii.academic_section_snapshot.v1",
          "sectionId":"$SECTION_ID","sectionCode":"SEC-01","status":"open","timezone":"America/Mexico_City",
          "term":{"sourceId":"00000000-0000-4000-8000-000000000002","code":"2026-A","startsOn":"2026-01-01","endsOn":"2026-12-31"},
          "subject":{"sourceId":"00000000-0000-4000-8000-000000000003","code":"MAT-01","name":"Mathematics","credits":6},
          "teachers":[{"sourceId":"00000000-0000-4000-8000-000000000004","externalId":"EMP-01","fullName":"Fixture Teacher"}],
          "enrollments":[
            {"sourceId":"00000000-0000-4000-8000-000000000005","studentSourceId":"00000000-0000-4000-8000-000000000006","studentExternalId":"STU-01","fullName":"Fixture Student","status":"enrolled"},
            {"sourceId":"00000000-0000-4000-8000-000000000007","studentSourceId":"00000000-0000-4000-8000-000000000008","studentExternalId":"STU-02","fullName":"Private Peer","status":"enrolled"}
          ],
          "occurrences":[{"sourceId":"00000000-0000-4000-8000-000000000009","patternSourceId":"00000000-0000-4000-8000-000000000010","date":"2026-10-17","startsAt":"2026-10-18T05:00:00Z","endsAt":"2026-10-18T06:00:00Z","status":"SCHEDULED",
            "room":{"sourceId":"00000000-0000-4000-8000-000000000011","code":"ROOM-01","building":"Building One","campusSourceId":"00000000-0000-4000-8000-000000000012","campusName":"Fixture Campus"}}]
        }
        """.trimIndent(),
    ) as JsonObject

internal fun JsonObject.withField(
    key: String,
    value: String,
): JsonObject = JsonObject(this + (key to JsonPrimitive(value)))

class SiiiSectionSnapshotTest {
    @Test
    fun `source snapshot preserves persisted IDs and exact next-day midnight boundary`() {
        val value = SiiiSectionSnapshotCodec.decode(sectionPayload(), SECTION_ID)
        assertEquals("2026-10-18T06:00:00Z", value.occurrences.single().endsAt)
        assertEquals("STU-02", value.enrollments.last().studentExternalId)
    }

    @Test
    fun `unknown fields invalid vocabulary wrong tenant-like payload and IDs fail closed`() {
        val valid = sectionPayload()
        val invalid =
            listOf(
                valid.withField("tenantId", "other"),
                valid.withField("sectionId", "not-uuid"),
                valid.withField("status", "active"),
                valid.withField("timezone", "Mars/Unknown"),
                valid.withField("sectionCode", "lowercase"),
                valid.withField("sectionCode", "A\nB"),
            )
        invalid.forEach { assertFailsWith<IllegalArgumentException> { SiiiSectionSnapshotCodec.decode(it, SECTION_ID) } }
        assertFailsWith<IllegalArgumentException> { SiiiSectionSnapshotCodec.decode(valid, "00000000-0000-4000-8000-000000000099") }
    }

    @Test
    fun `open section before teacher assignment is a valid complete source snapshot`() {
        val payload = JsonObject(sectionPayload() + ("teachers" to JsonArray(emptyList())))
        assertEquals(emptyList(), SiiiSectionSnapshotCodec.decode(payload, SECTION_ID).teachers)
    }

    @Test
    fun `invalid local date seconds and beyond-midnight end are rejected`() {
        val valid = sectionPayload()
        val occurrence = (valid["occurrences"] as JsonArray).single() as JsonObject
        listOf(
            occurrence.withField("date", "2026-10-18"),
            occurrence.withField("date", "2026-02-30"),
            occurrence.withField("startsAt", "2026-10-18T05:00:01Z"),
            occurrence.withField("endsAt", "2026-10-18T06:01:00Z"),
            occurrence.withField("endsAt", "2026-10-18T04:00:00Z"),
            occurrence.withField("endsAt", "+1000000000-12-31T23:59:59.999999999Z"),
            occurrence.withField("status", "scheduled"),
        ).forEach {
            assertFailsWith<IllegalArgumentException> {
                SiiiSectionSnapshotCodec.decode(
                    JsonObject(
                        valid + ("occurrences" to JsonArray(listOf(it))),
                    ),
                    SECTION_ID,
                )
            }
        }
    }

    @Test
    fun `subject credits must be a bounded JSON integer`() {
        val valid = sectionPayload()
        val subject = valid["subject"] as JsonObject
        listOf(JsonPrimitive("6"), JsonPrimitive(-1), JsonPrimitive(31)).forEach { credit ->
            assertFailsWith<IllegalArgumentException> {
                SiiiSectionSnapshotCodec.decode(JsonObject(valid + ("subject" to JsonObject(subject + ("credits" to credit)))), SECTION_ID)
            }
        }
    }

    @Test
    fun `withdrawn history is valid but duplicate active identity and inconsistent aliases fail`() {
        val valid = sectionPayload()
        val rows = valid["enrollments"] as JsonArray
        val first = rows.first() as JsonObject
        val history = first.withField("sourceId", "00000000-0000-4000-8000-000000000099").withField("status", "withdrawn")
        assertEquals(
            3,
            SiiiSectionSnapshotCodec.decode(JsonObject(valid + ("enrollments" to JsonArray(rows + history))), SECTION_ID).enrollments.size,
        )
        listOf(history.withField("status", "enrolled"), history.withField("studentExternalId", "ALIAS-01"), first).forEach {
            assertFailsWith<IllegalArgumentException> {
                SiiiSectionSnapshotCodec.decode(
                    JsonObject(
                        valid + ("enrollments" to JsonArray(rows + it)),
                    ),
                    SECTION_ID,
                )
            }
        }
    }

    @Test
    fun `canonical digest ignores object and roster order and equivalent instant spelling`() {
        val valid = sectionPayload()
        val occurrence = (valid["occurrences"] as JsonArray).single() as JsonObject
        val reordered =
            JsonObject(
                valid.entries.reversed().associate { it.key to it.value } +
                    mapOf(
                        "enrollments" to JsonArray((valid["enrollments"] as JsonArray).reversed()),
                        "occurrences" to JsonArray(listOf(occurrence.withField("startsAt", "2026-10-18T05:00:00.000Z"))),
                    ),
            )
        val original = SiiiSectionSnapshotCodec.decode(valid, SECTION_ID)
        assertEquals(
            SiiiSectionSnapshotCodec.digest(original),
            SiiiSectionSnapshotCodec.digest(SiiiSectionSnapshotCodec.decode(reordered, SECTION_ID)),
        )
        assertNotEquals(
            SiiiSectionSnapshotCodec.digest(original),
            SiiiSectionSnapshotCodec.digest(SiiiSectionSnapshotCodec.decode(valid.withField("sectionCode", "SEC-02"), SECTION_ID)),
        )
    }
}
