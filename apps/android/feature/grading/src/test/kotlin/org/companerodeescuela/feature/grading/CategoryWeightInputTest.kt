package org.companerodeescuela.feature.grading

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CategoryWeightInputTest {
    @Test
    fun `clearing a percentage stays editable instead of restoring its previous value`() {
        assertNull(categoryWeightValue(""))
        assertNull(categoryWeightValue(" "))
        assertEquals(2.0, categoryWeightValue("2"))
        assertEquals(25.0, categoryWeightValue("25"))
    }

    @Test
    fun `decimal input supports intermediate separator and Spanish comma`() {
        assertEquals(2.0, categoryWeightValue("2."))
        assertEquals(2.5, categoryWeightValue("2.5"))
        assertEquals(2.0, categoryWeightValue("2,"))
        assertEquals(2.5, categoryWeightValue("2,5"))
        assertEquals(0.5, categoryWeightValue(",5"))
    }

    @Test
    fun `invalid drafts cannot silently save the previous valid percentage`() {
        listOf("101", "-1", "NaN", "Infinity", "1e2", "2..5", "2,5.0", "letters").forEach { raw ->
            assertNull(categoryWeightValue(raw), raw)
        }
        assertEquals(0.0, categoryWeightValue("0"))
        assertEquals(100.0, categoryWeightValue("100"))
    }

    @Test
    fun `only current category drafts block saving after a category is removed`() {
        val drafts = mapOf("exam" to "20,5", "tasks" to "", "removed" to "invalid")
        assertFalse(categoryWeightDraftsValid(listOf("exam", "tasks"), drafts))
        assertTrue(categoryWeightDraftsValid(listOf("exam"), drafts))
        assertTrue(categoryWeightDraftsValid(listOf("new"), drafts))
    }
}
