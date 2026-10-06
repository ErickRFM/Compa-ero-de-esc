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
    fun allQaRolesReceiveCleanAcademicState() = runTest {
        listOf(
            MockFixtures.QA_STUDENT_ID,
            MockFixtures.QA_TEACHER_ID,
            MockFixtures.QA_SUPERVISOR_ID,
            MockFixtures.QA_ADMIN_ID,
        ).forEach { externalId ->
            val load = provider.getAcademicLoad(externalId)
            assertEquals(externalId, load.student.externalId)
            assertTrue(load.enrollments.isEmpty())
            assertTrue(load.schedule.isEmpty())
            assertTrue(provider.getSchedule(externalId).isEmpty())
        }
    }

    @Test
    fun demoStudentKeepsExplicitDevelopmentFixture() = runTest {
        val load = provider.getAcademicLoad(MockFixtures.STUDENT_ID)

        assertTrue(load.enrollments.isNotEmpty())
        assertTrue(load.schedule.isNotEmpty())
    }
}
