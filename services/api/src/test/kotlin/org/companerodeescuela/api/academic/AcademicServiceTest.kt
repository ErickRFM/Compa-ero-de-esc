package org.companerodeescuela.api.academic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.integrations.mock.MockAcademicProvider

class AcademicServiceTest {

    @Test
    fun `mock academic load becomes a stable client contract`() = runTest {
        val response = AcademicService(MockAcademicProvider())
            .loadFor("2020-10455")

        assertEquals("2020-10455", response.student.id)
        assertEquals("Ana López Hernández", response.student.displayName)
        assertEquals(3, response.schedule.entries.size)
        assertEquals("Álgebra Lineal", response.schedule.entries.first().subjectName)
        assertEquals("A-204", response.schedule.entries.first().classroomName)
    }

    @Test
    fun `missing academic identity becomes public not found`() = runTest {
        assertFailsWith<ApiException.NotFound> {
            AcademicService(MockAcademicProvider()).loadFor("missing")
        }
    }
}
