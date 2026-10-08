package org.companerodeescuela.api.grading

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import org.companerodeescuela.shared.contracts.GradeCategoryDraft

class GradeCalculatorTest {
    @Test
    fun weightedFinalRequiresOneHundredPercentAndCalculatesFinal() {
        val scheme = listOf(
            GradeCategoryDraft("Proyecto", 30.0),
            GradeCategoryDraft("Examen", 25.0),
            GradeCategoryDraft("Tareas", 20.0),
            GradeCategoryDraft("Practicas", 15.0),
            GradeCategoryDraft("Participacion", 10.0),
        )

        assertEquals(
            8.88,
            GradeCalculator.weightedFinal(
                scheme,
                mapOf(
                    "Proyecto" to 9.0,
                    "Examen" to 8.5,
                    "Tareas" to 10.0,
                    "Practicas" to 8.0,
                    "Participacion" to 8.5,
                ),
            ),
        )
    }

    @Test
    fun rejectsIncompleteWeightScheme() {
        assertFails {
            GradeCalculator.validateScheme(
                listOf(
                    GradeCategoryDraft("Proyecto", 30.0),
                    GradeCategoryDraft("Examen", 30.0),
                ),
            )
        }
    }
}
