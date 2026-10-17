package org.companerodeescuela.api.integrations.academic.siii

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.companerodeescuela.api.integrations.academic.dto.ExternalAcademicLoad
import org.companerodeescuela.api.integrations.academic.dto.ExternalCourse
import org.companerodeescuela.api.integrations.academic.dto.ExternalEnrollment
import org.companerodeescuela.api.integrations.academic.dto.ExternalScheduleSlot
import org.companerodeescuela.api.integrations.academic.dto.ExternalStudent
import org.companerodeescuela.api.integrations.academic.dto.ExternalSubject
import org.companerodeescuela.api.integrations.academic.dto.ExternalTeacher
import org.companerodeescuela.api.integrations.academic.dto.ExternalWeekdays
import org.companerodeescuela.api.integrations.academic.dto.parseUpstreamTime
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.Instant

/** Wire-only schemas: changes here never change the existing external or mobile DTOs. */
internal object SiiiAcademicV1 {
    private val json =
        Json {
            ignoreUnknownKeys = false
            isLenient = false
            coerceInputValues = false
        }

    fun student(bytes: ByteArray): ExternalStudent = decode<StudentV1>(bytes).external()

    fun courses(bytes: ByteArray): List<ExternalCourse> =
        decode<List<CourseV1>>(bytes).also { require(it.size <= 200) }.map { it.external() }

    fun teachers(bytes: ByteArray): List<ExternalTeacher> =
        decode<List<TeacherV1>>(bytes)
            .also {
                require(it.size <= 200)
            }.map { it.external() }

    fun schedule(bytes: ByteArray): List<ExternalScheduleSlot> = decode<List<SlotV1>>(bytes).map { it.external() }

    fun load(bytes: ByteArray): ExternalAcademicLoad {
        val wire = decode<LoadV1>(bytes)
        require(wire.enrollments.size <= 100)
        val enrollments =
            wire.enrollments.map {
                Instant.parse(it.enrolledAtIso)
                ExternalEnrollment(it.course.external(), it.enrolledAtIso)
            }
        val courses = enrollments.map { it.course.externalId }.toSet()
        require(courses.size == enrollments.size)
        val schedule = wire.schedule.map { it.external() }
        require(schedule.all { it.courseId in courses })
        return ExternalAcademicLoad(wire.student.external(), enrollments, schedule)
    }

    private inline fun <reified T> decode(bytes: ByteArray): T {
        val text =
            Charsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        return json.decodeFromString<T>(text)
    }

    private fun valid(vararg fields: String) = require(fields.all { it.isNotBlank() && it.none(Char::isISOControl) })

    @Serializable
    private data class StudentV1(
        val externalId: String,
        val fullName: String,
        val institutionalEmail: String? = null,
        val programCode: String? = null,
        val enrollmentYear: Int? = null,
    ) {
        fun external(): ExternalStudent {
            valid(externalId, fullName)
            return ExternalStudent(externalId, fullName, institutionalEmail, programCode, enrollmentYear)
        }
    }

    @Serializable
    private data class TeacherV1(
        val externalId: String,
        val fullName: String,
        val institutionalEmail: String? = null,
        val departmentCode: String? = null,
    ) {
        fun external(): ExternalTeacher {
            valid(externalId, fullName)
            return ExternalTeacher(externalId, fullName, institutionalEmail, departmentCode)
        }
    }

    @Serializable
    private data class SubjectV1(
        val externalCode: String,
        val name: String,
        val credits: Int? = null,
    ) {
        fun external(): ExternalSubject {
            valid(externalCode, name)
            require(credits == null || credits >= 0)
            return ExternalSubject(externalCode, name, credits)
        }
    }

    @Serializable
    private data class CourseV1(
        val externalId: String,
        val subject: SubjectV1,
        val teacher: TeacherV1,
        val term: String,
        val groupName: String,
    ) {
        fun external(): ExternalCourse {
            valid(externalId, term, groupName)
            return ExternalCourse(externalId, subject.external(), teacher.external(), term, groupName)
        }
    }

    @Serializable
    private data class EnrollmentV1(
        val course: CourseV1,
        val enrolledAtIso: String,
    )

    @Serializable
    private data class LoadV1(
        val student: StudentV1,
        val enrollments: List<EnrollmentV1>,
        val schedule: List<SlotV1>,
    )

    @Serializable
    private data class SlotV1(
        val courseId: String,
        val groupName: String,
        val weekdayName: String,
        val startTime: String,
        val endTime: String,
        val buildingCode: String? = null,
        val classroomCode: String? = null,
        val campusName: String? = null,
    ) {
        fun external(): ExternalScheduleSlot {
            valid(courseId, groupName)
            require(ExternalWeekdays.fromName(weekdayName) != null)
            val start = requireNotNull(parseUpstreamTime(startTime))
            val end = requireNotNull(parseUpstreamTime(endTime))
            require(start < end)
            return ExternalScheduleSlot(courseId, groupName, weekdayName, startTime, endTime, buildingCode, classroomCode, campusName)
        }
    }
}
