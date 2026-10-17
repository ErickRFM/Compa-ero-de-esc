package org.companerodeescuela.api.integrations.academic

import kotlinx.coroutines.runBlocking
import org.companerodeescuela.api.integrations.academic.siii.SiiiAcademicProvider
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.net.URI
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Opt-in contract probe for a real disposable Siii Next/PostgreSQL lab, never a deployed origin. */
@EnabledIfEnvironmentVariable(named = "SIII_LIVE_TEST_BASE_URL", matches = ".+")
class SiiiLiveContractTest {
    @Test
    fun `real Siii handlers preserve student enrollment teacher and Saturday classroom contracts`() =
        runBlocking {
            val origin = URI(required("SIII_LIVE_TEST_BASE_URL"))
            require(origin.host in setOf("127.0.0.1", "localhost", "[::1]", "::1")) { "Live contract probe requires loopback" }
            val studentId = required("SIII_LIVE_TEST_STUDENT_ID")
            val courseId = required("SIII_LIVE_TEST_COURSE_ID")
            val teacherId = required("SIII_LIVE_TEST_TEACHER_ID")
            val classroom = required("SIII_LIVE_TEST_CLASSROOM_CODE")
            val weekday = required("SIII_LIVE_TEST_WEEKDAY")
            val provider = SiiiAcademicProvider(origin, required("SIII_LIVE_TEST_TOKEN"), allowInsecureLoopback = true)

            val student = assertNotNull(provider.getStudent(studentId))
            assertEquals(studentId, student.externalId)
            val load = provider.getAcademicLoad(studentId)
            assertEquals(studentId, load.student.externalId)
            val enrollment = load.enrollments.single { it.course.externalId == courseId }
            assertEquals(teacherId, enrollment.course.teacher.externalId)
            assertTrue(provider.listCourses().any { it.externalId == courseId && it.teacher.externalId == teacherId })
            assertTrue(provider.listTeachers().any { it.externalId == teacherId })
            for (slots in listOf(load.schedule, provider.getSchedule(studentId, LocalDate.of(2026, 10, 17)))) {
                val slot = slots.single { it.courseId == courseId && it.classroomCode == classroom && it.weekdayName == weekday }
                assertEquals("10:00", slot.startTime)
                assertEquals("11:00", slot.endTime)
            }
        }

    private fun required(name: String): String =
        requireNotNull(
            System.getenv(name)?.takeIf {
                it.isNotBlank()
            },
        ) { "Missing synthetic lab variable: $name" }
}
