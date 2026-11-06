package org.companerodeescuela.shared.contracts

import kotlinx.serialization.Serializable

/** Shared wire shape only. Sending this envelope never publishes a grade in SIII. */
@Serializable
data class InstitutionalGradeProposal(
    val contract: String,
    val operationId: String,
    val sourceVersion: Long,
    val sourceDigest: String,
    val institutionId: String,
    val nativeClassroomId: String,
    val sectionId: String,
    val academicPeriodId: String,
    val teacherExternalId: String,
    val gradingPeriod: String,
    val gradeScale: String,
    val rows: List<InstitutionalGradeProposalRow>,
) {
    companion object {
        const val CONTRACT = "companero.grade_proposal.v1"
        const val GRADE_SCALE = "MX_0_TO_10_HUNDREDTHS"
    }
}

@Serializable
data class InstitutionalGradeProposalRow(
    val nativeStudentId: String,
    val studentSourceId: String,
    /** Exact 0..10 Mexican grade, represented as 0..1000 hundredths. Never Double. */
    val gradeHundredths: Int,
)

@Serializable
enum class InstitutionalGradeProposalState {
    RECEIVED,
    VALIDATED,
    REQUIRES_REVIEW,
    REJECTED,
    APPLIED,
}

/** Local preflight before Ktor adds a signed transport and an institutional inbox.
 * It rejects malformed metadata but does NOT authorize student/teacher mappings.
 */
object InstitutionalGradeProposalValidation {
    private val nativeId = Regex("[A-Za-z0-9:_-]{6,96}")
    private val externalId = Regex("[A-Za-z0-9_-]{2,64}")
    private val periodId = Regex("[A-Za-z0-9_-]{1,48}")
    private val digest = Regex("[a-f0-9]{64}")
    private val uuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

    fun problems(value: InstitutionalGradeProposal): Set<String> {
        val result = linkedSetOf<String>()
        if (value.contract != InstitutionalGradeProposal.CONTRACT || value.gradeScale != InstitutionalGradeProposal.GRADE_SCALE) {
            result.add("UNSUPPORTED_CONTRACT")
        }
        if (!uuid.matches(value.operationId) || !uuid.matches(value.institutionId) ||
            !uuid.matches(value.sectionId) || !uuid.matches(value.academicPeriodId)
        ) {
            result.add("INVALID_INSTITUTIONAL_ID")
        }
        if (value.sourceVersion < 1 || !digest.matches(value.sourceDigest)) result.add("INVALID_VERSION")
        if (!nativeId.matches(value.nativeClassroomId) || !externalId.matches(value.teacherExternalId) ||
            !periodId.matches(value.gradingPeriod)
        ) {
            result.add("INVALID_SOURCE_ID")
        }
        if (value.rows.size !in 1..500) result.add("BATCH_SIZE")
        val nativeStudents = mutableSetOf<String>()
        val officialStudents = mutableSetOf<String>()
        for (row in value.rows) {
            if (!nativeId.matches(row.nativeStudentId) || !uuid.matches(row.studentSourceId)) {
                result.add("INVALID_STUDENT_ID")
            }
            if (row.gradeHundredths !in 0..1000) result.add("INVALID_GRADE")
            if (!nativeStudents.add(row.nativeStudentId) || !officialStudents.add(row.studentSourceId)) {
                result.add("DUPLICATE_STUDENT")
            }
        }
        return result
    }
}
