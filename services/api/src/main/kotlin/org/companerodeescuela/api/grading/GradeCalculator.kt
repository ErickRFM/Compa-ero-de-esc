package org.companerodeescuela.api.grading

import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.GradeCategoryDraft
import kotlin.math.round

object GradeCalculator {
    fun validateScheme(categories: List<GradeCategoryDraft>) {
        if (categories.isEmpty()) throw ApiException.Validation("At least one grading category is required")
        categories.forEach {
            if (it.name.isBlank()) throw ApiException.Validation("Category name is required")
            if (it.weightPercent <= 0.0 || it.weightPercent > 100.0) {
                throw ApiException.Validation("Each category weight must be greater than 0 and at most 100")
            }
        }
        val total = categories.sumOf { it.weightPercent }
        if (kotlin.math.abs(total - 100.0) > 0.001) {
            throw ApiException.Validation("Grading weights must add up to 100%")
        }
    }

    fun weightedFinal(categories: List<GradeCategoryDraft>, values: Map<String, Double>): Double {
        validateScheme(categories)
        val result = categories.sumOf { category ->
            val value = values[category.name]
                ?: throw ApiException.Validation("Missing grade for ${category.name}")
            if (value !in 0.0..10.0) throw ApiException.Validation("Grades must be between 0 and 10")
            value * (category.weightPercent / 100.0)
        }
        return round(result * 100.0) / 100.0
    }
}
