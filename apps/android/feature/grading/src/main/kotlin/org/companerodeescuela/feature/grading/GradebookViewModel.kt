package org.companerodeescuela.feature.grading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.round
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.shared.contracts.GradeCategoryDraft
import org.companerodeescuela.shared.contracts.GradeImportPreview
import org.companerodeescuela.shared.contracts.GradeSyncRequest
import org.companerodeescuela.shared.contracts.GradeSyncRow

data class GradebookUiState(
    val classroomId: String = "",
    val gradingPeriod: String = "",
    val categories: List<GradeCategoryDraft> = emptyList(),
    val preview: GradeImportPreview? = null,
    val importedFileName: String? = null,
    val submitting: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
) {
    val totalWeight: Double get() = categories.sumOf { it.weightPercent }
    val schemeComplete: Boolean get() = categories.isNotEmpty() && kotlin.math.abs(totalWeight - 100.0) <= 0.001
}

@HiltViewModel
class GradebookViewModel @Inject constructor(
    private val repository: GradebookRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(GradebookUiState())
    val state: StateFlow<GradebookUiState> = _state.asStateFlow()

    fun setClassroomId(value: String) = _state.update { it.copy(classroomId = value.trim().take(120)) }
    fun setGradingPeriod(value: String) = _state.update { it.copy(gradingPeriod = value.take(80)) }

    fun addCategory() = _state.update {
        it.copy(categories = it.categories + GradeCategoryDraft("", 0.0), errorMessage = null)
    }

    fun removeCategory(index: Int) = _state.update {
        it.copy(categories = it.categories.filterIndexed { position, _ -> position != index })
    }

    fun updateCategory(index: Int, name: String? = null, weight: Double? = null) = _state.update { current ->
        current.copy(
            categories = current.categories.mapIndexed { position, category ->
                if (position != index) category
                else category.copy(
                    name = name?.take(80) ?: category.name,
                    weightPercent = weight ?: category.weightPercent,
                )
            },
            errorMessage = null,
        )
    }

    fun importSpreadsheet(fileName: String, bytes: ByteArray) {
        runCatching { SpreadsheetReader.read(fileName, bytes) }
            .onSuccess { preview ->
                _state.update {
                    it.copy(
                        preview = preview,
                        importedFileName = fileName,
                        errorMessage = null,
                        successMessage = "${preview.rows.size} alumnos leídos. Revisa antes de sincronizar.",
                    )
                }
            }
            .onFailure { error ->
                _state.update {
                    it.copy(errorMessage = error.message ?: "No se pudo leer el archivo.", successMessage = null)
                }
            }
    }

    fun sync() {
        val current = _state.value
        if (current.submitting) return
        if (!current.schemeComplete) {
            _state.update { it.copy(errorMessage = "La ponderación debe sumar exactamente 100%.") }
            return
        }
        if (current.classroomId.isBlank() || current.gradingPeriod.isBlank()) {
            _state.update { it.copy(errorMessage = "Selecciona/indica la clase y el periodo de evaluación.") }
            return
        }
        val preview = current.preview ?: run {
            _state.update { it.copy(errorMessage = "Importa un Excel/CSV o captura las calificaciones antes de sincronizar.") }
            return
        }

        val rows = mutableListOf<GradeSyncRow>()
        val missing = mutableListOf<String>()
        preview.rows.forEach { student ->
            val finalGrade = weightedFinal(current.categories, student.values)
            if (finalGrade == null) missing += student.studentId
            else rows += GradeSyncRow(studentId = student.studentId, finalGrade = finalGrade)
        }
        if (missing.isNotEmpty()) {
            _state.update {
                it.copy(errorMessage = "Faltan valores válidos para ${missing.size} alumnos. Verifica que los nombres de las columnas coincidan con las categorías.")
            }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(submitting = true, errorMessage = null, successMessage = null) }
            when (
                val result = repository.sync(
                    GradeSyncRequest(
                        classroomId = current.classroomId,
                        gradingPeriod = current.gradingPeriod,
                        rows = rows,
                    ),
                )
            ) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        submitting = false,
                        successMessage = "${result.value.acceptedStudentIds.size} calificaciones sincronizadas.",
                    )
                }
                is Outcome.Failure -> _state.update {
                    it.copy(submitting = false, errorMessage = result.error.userMessage)
                }
            }
        }
    }

    private fun weightedFinal(
        categories: List<GradeCategoryDraft>,
        values: Map<String, Double?>,
    ): Double? {
        var total = 0.0
        for (category in categories) {
            val value = values.entries.firstOrNull { it.key.equals(category.name, ignoreCase = true) }?.value
                ?: return null
            if (value !in 0.0..10.0) return null
            total += value * (category.weightPercent / 100.0)
        }
        return round(total * 100.0) / 100.0
    }
}
