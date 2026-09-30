package org.companerodeescuela.api.integrations.lms

import org.companerodeescuela.api.integrations.IntegrationProvider

/** A course as the learning management system sees it. */
data class ExternalLearningModule(
    val externalId: String,
    val title: String,
    val academicCourseId: String?,
    val openDateIso: String? = null,
    val closeDateIso: String? = null,
)

/** An assignment inside a learning module. */
data class ExternalAssignment(
    val externalId: String,
    val moduleId: String,
    val title: String,
    val dueDateIso: String? = null,
    val maxScore: Double? = null,
)

/**
 * Adapter for the learning management system (Moodle, Canvas, Blackboard).
 *
 * Kept separate from [org.companerodeescuela.api.integrations.academic.AcademicProvider]
 * because the two have different owners, different availability guarantees and
 * different failure modes: a course can be enrolled while its LMS module is
 * down, and the student must still see their timetable.
 */
interface LearningProvider : IntegrationProvider {
    suspend fun listModules(enrollmentId: String): List<ExternalLearningModule>

    suspend fun listAssignments(moduleId: String): List<ExternalAssignment>
}
