package org.companerodeescuela.api.integrations.academic

import java.time.LocalDate
import org.companerodeescuela.api.integrations.IntegrationProvider
import org.companerodeescuela.api.integrations.academic.dto.ExternalAcademicLoad
import org.companerodeescuela.api.integrations.academic.dto.ExternalCourse
import org.companerodeescuela.api.integrations.academic.dto.ExternalScheduleSlot
import org.companerodeescuela.api.integrations.academic.dto.ExternalStudent
import org.companerodeescuela.api.integrations.academic.dto.ExternalTeacher

/**
 * Adapter for the institution's enrolment and records system.
 *
 * This is the single seam through which academic data enters the platform.
 * Adding Moodle, Banner or a bespoke registrar means adding a new
 * implementation of this interface, never changing callers.
 *
 * Every method returns external DTOs. Mapping to domain models is the
 * repository's job, which keeps upstream quirks out of the rest of the code.
 *
 * Implementations must throw
 * [org.companerodeescuela.api.integrations.IntegrationException] rather than
 * leaking transport or vendor exception types.
 */
interface AcademicProvider : IntegrationProvider {

    /** Looks up one student by their identifier in the institutional system. */
    suspend fun getStudent(externalId: String): ExternalStudent?

    /**
     * Returns the full academic load: enrolled courses plus the weekly
     * schedule. Institutions normally expose this as one call, and splitting it
     * would double the number of round trips on the mobile client's critical
     * path.
     */
    suspend fun getAcademicLoad(externalId: String): ExternalAcademicLoad

    /** All courses visible to the authenticated context, for catalogue screens. */
    suspend fun listCourses(): List<ExternalCourse>

    /**
     * Weekly schedule for a student or teacher.
     *
     * @param externalId subject of the schedule.
     * @param weekOf any date inside the requested week; providers that cannot
     *   resolve a specific week must ignore it rather than fail.
     */
    suspend fun getSchedule(externalId: String, weekOf: LocalDate? = null): List<ExternalScheduleSlot>

    /** Staff directory, used to resolve teacher names for subjects. */
    suspend fun listTeachers(): List<ExternalTeacher>
}
