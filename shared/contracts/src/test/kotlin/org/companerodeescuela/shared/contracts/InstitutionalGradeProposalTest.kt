package org.companerodeescuela.shared.contracts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class InstitutionalGradeProposalTest {
    private fun id() = java.util.UUID.randomUUID().toString()

    private fun proposal(): InstitutionalGradeProposal =
        InstitutionalGradeProposal(
            operationId = id(),
            sourceVersion = 1,
            sourceDigest = "a".repeat(64),
            institutionId = id(),
            nativeClassroomId = "native-class-001",
            sectionId = id(),
            academicPeriodId = id(),
            teacherExternalId = "EMP-001",
            gradingPeriod = "C1",
            rows = listOf(InstitutionalGradeProposalRow("native-student-001", id(), 875)),
        )

    @Test
    fun `serializes the exact Companion to SIII envelope without floating grades`() {
        val value = proposal()
        val wire = Json.encodeToString(value)
        val decoded = Json.decodeFromString<InstitutionalGradeProposal>(wire)
        assertEquals(value, decoded)
        assertTrue(wire.contains("\"gradeHundredths\":875"))
        assertFalse(wire.contains("finalGrade"))
        assertTrue(InstitutionalGradeProposalValidation.problems(decoded).isEmpty())
    }

    @Test
    fun `validates exact grade endpoints and refuses out-of-range values`() {
        for (score in listOf(0, 1000)) {
            val candidate = proposal().copy(
                rows = listOf(InstitutionalGradeProposalRow("native-student-001", id(), score)),
            )
            assertTrue(InstitutionalGradeProposalValidation.problems(candidate).isEmpty())
        }
        for (score in listOf(-1, 1001)) {
            val candidate = proposal().copy(
                rows = listOf(InstitutionalGradeProposalRow("native-student-001", id(), score)),
            )
            assertTrue("INVALID_GRADE" in InstitutionalGradeProposalValidation.problems(candidate))
        }
    }

    @Test
    fun `refuses duplicated source students and official students`() {
        val first = InstitutionalGradeProposalRow("native-student-001", id(), 800)
        val second = InstitutionalGradeProposalRow("native-student-002", first.studentSourceId, 700)
        val duplicateOfficial = proposal().copy(rows = listOf(first, second))
        assertTrue("DUPLICATE_STUDENT" in InstitutionalGradeProposalValidation.problems(duplicateOfficial))
        val duplicateNative = proposal().copy(
            rows = listOf(first, second.copy(studentSourceId = id(), nativeStudentId = first.nativeStudentId)),
        )
        assertTrue("DUPLICATE_STUDENT" in InstitutionalGradeProposalValidation.problems(duplicateNative))
    }

    @Test
    fun `rejects invalid envelope and large batches before transport`() {
        val original = proposal()
        val incorrect = original.copy(
            sourceVersion = 0,
            sourceDigest = "none",
            contract = "unknown",
            rows = emptyList(),
        )
        val found = InstitutionalGradeProposalValidation.problems(incorrect)
        assertTrue(found.containsAll(setOf("INVALID_VERSION", "UNSUPPORTED_CONTRACT", "BATCH_SIZE")))
        val tooLarge = original.copy(
            rows = List(501) {
                InstitutionalGradeProposalRow(
                    nativeStudentId = "native-student-$it",
                    studentSourceId = id(),
                    gradeHundredths = 700,
                )
            },
        )
        assertTrue("BATCH_SIZE" in InstitutionalGradeProposalValidation.problems(tooLarge))
    }

    @Test
    fun `proposal statuses never imply official publication before APPLIED`() {
        val states = InstitutionalGradeProposalState.entries.map { it.name }
        assertEquals(listOf("RECEIVED", "VALIDATED", "REQUIRES_REVIEW", "REJECTED", "APPLIED"), states)
    }
}
