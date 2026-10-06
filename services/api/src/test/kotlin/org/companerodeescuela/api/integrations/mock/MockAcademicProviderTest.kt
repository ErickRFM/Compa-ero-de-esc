package org.companerodeescuela.api.integrations.mock

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MockAcademicProviderTest {
    private val provider = MockAcademicProvider()

    @Test
    fun qaStudentReceivesEmptyAcademicLoad() = runTest {
        val load = provider.getAcademicLoad(MockFixtures.QA_STUDENT_ID)

        assertEquals(MockFixtures.QA_STUDENT_ID, load.student.externalId)
        assertTrue(load.enrollments.isEmpty())
        assertTrue(load.schedule.isEmpty())
    }

    @Test
    fun qaTeacherDoesNotInheritDemoSchedule() = runTest {
        assertTrue(provider.getSchedule(MockFixtures.QA_TEACHER_ID).isEmpty())
    }

    @Test
    fun demoStudentKeepsExplicitDevelopmentFixture() = runTest {
        val load = provider.getAcademicLoad(MockFixtures.STUDENT_ID)

        assertTrue(load.enrollments.isNotEmpty())
        assertTrue(load.schedule.isNotEmpty())
    }
}
