package org.companerodeescuela.shared.contracts

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class InstitutionalGradeProposalTest {
    private fun id() = UUID.randomUUID().toString()

    private fun proposal() = InstitutionalGradeProposal(
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
            assertTrue(InstitutionalGradeProposalValidation.problems(
                proposal().copy(rows = listOf(InstitutionalGradeProposalRow("native-student-001", id(), score))),
            ).isEmpty())
        }
        for (score in listOf(-1, 1001)) {
            assertTrue("INVALID_GRADE" in InstitutionalGradeProposalValidation.problems(
                proposal().copy(rows = listOf(InstitutionalGradeProposalRow("native-student-001", id(), score))),
            ))
        }
    }

    @Test
    fun `refuses duplicated source students and official students`() {
        val first = InstitutionalGradeProposalRow("native-student-001", id(), 800)
        val second = InstitutionalGradeProposalRow("native-student-002", first.studentSourceId, 700)
        val duplicateOfficial = proposal().copy(rows = listOf(first, second))
        assertTrue("DUPLICATE_STUDENT" in InstitutionalGradeProposalValidation.problems(duplicateOfficial))
        val duplicateNative = proposal().copy(rows = listOf(first, second.copy(studentSourceId = id(), nativeStudentId = first.nativeStudentId)))
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
        assertTrue(InstitutionalGradeProposalValidation.problems(incorrect).containsAll(
            setOf("INVALID_VERSION", "UNSUPPORTED_CONTRACT", "BATCH_SIZE"),
        ))
        assertTrue("BATCH_SIZE" in InstitutionalGradeProposalValidation.problems(
            original.copy(rows = List(501) { InstitutionalGradeProposalRow("native-student-$it", id(), 700) }),
        ))
    }

    @Test
    fun `proposal statuses never imply official publication before APPLIED`() {
        assertEquals(
            listOf("RECEIVED", "VALIDATED", "REQUIRES_REVIEW", "REJECTED", "APPLIED"),
            InstitutionalGradeProposalState.entries.map { it.name },
        )
    }
}
