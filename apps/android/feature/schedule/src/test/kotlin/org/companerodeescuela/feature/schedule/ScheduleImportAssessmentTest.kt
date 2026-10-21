package org.companerodeescuela.feature.schedule

import org.companerodeescuela.core.academic.PersonalScheduleDraft
import org.companerodeescuela.shared.contracts.ScheduleSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScheduleImportAssessmentTest {
    private fun entry(day: String) =
        PersonalScheduleDraft(
            subjectName = "Sistemas Embebidos",
            teacherName = "Ivette Hernández",
            dayOfWeek = day,
            startsAt = "13:00",
            endsAt = "14:00",
            source = ScheduleSource.OCR_IMPORT,
        )

    @Test fun golden9AHasSixDaysAnd25MergedBlocks() {
        val fixture = requireNotNull(javaClass.getResource("/fixtures/9A-ocr.txt")).readText()
        val complete = ScheduleOcrParser.parse(fixture)
        assertEquals(25, complete.size)
        assertTrue(ScheduleImportAssessment.missingDays(complete).isEmpty())
        assertFalse(ScheduleImportAssessment.shouldCrossCheckNative(complete))
    }

    @Test fun incompleteNativeTimetableRecoversMissingWeekdaysFromOcr() {
        val fixture = requireNotNull(javaClass.getResource("/fixtures/9A-ocr.txt")).readText()
        val complete = ScheduleOcrParser.parse(fixture)
        val partial =
            complete.filterNot {
                it.dayOfWeek in setOf("WEDNESDAY", "THURSDAY", "SATURDAY")
            }
        assertTrue(ScheduleImportAssessment.shouldCrossCheckNative(partial))
        assertEquals(complete, ScheduleImportAssessment.select(partial, complete))
        val warnings = ScheduleImportAssessment.warnings(partial, emptyList())
        assertTrue(warnings.first().contains("Mié"))
        assertTrue(warnings.first().contains("Jue"))
        assertTrue(warnings.first().contains("Sáb"))
    }

    @Test fun uncertainOcrCannotDiscardOriginalDayCoverage() {
        val native = listOf(entry("MONDAY"), entry("WEDNESDAY"), entry("THURSDAY"))
        val noisy = listOf(entry("MONDAY"), entry("FRIDAY"), entry("SATURDAY"))
        assertEquals(native, ScheduleImportAssessment.select(native, noisy))
        assertEquals(native, ScheduleImportAssessment.select(native, emptyList()))
    }

    @Test fun pdfUriWorksWhenDocumentProviderOmitsMime() {
        assertTrue(isPdfDocument(null, "9A.PDF"))
        assertTrue(isPdfDocument("application/pdf; charset=binary", "random"))
        assertFalse(isPdfDocument("image/jpeg", "photo.jpg"))
    }

    @Test fun incompleteWeekNeedsExplicitCoverageReview() {
        val warnings =
            ScheduleImportAssessment.warnings(
                listOf(entry("MONDAY"), entry("SATURDAY")),
                emptyList(),
            )
        assertTrue(warnings.isNotEmpty())
        assertTrue(warnings.first().contains("Mié"))
    }
}
