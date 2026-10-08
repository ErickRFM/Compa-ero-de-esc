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
import org.companerodeescuela.core.security.SessionTokenInspector
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.feature.classroom.ClassroomRepository
import org.companerodeescuela.shared.contracts.ClassroomStatus
import org.companerodeescuela.shared.contracts.ClassroomSummary
import org.companerodeescuela.shared.contracts.GradeCategoryDraft
import org.companerodeescuela.shared.contracts.GradeImportPreview
import org.companerodeescuela.shared.contracts.GradeSyncRequest
import org.companerodeescuela.shared.contracts.GradeSyncRow

data class GradebookUiState(
    val classroomId: String = "",
    val assignedClassrooms: List<ClassroomSummary> = emptyList(),
    val loadingClassrooms: Boolean = true,
    val classroomLoadError: String? = null,
    val gradingPeriod: String = "",
    val categories: List<GradeCategoryDraft> = emptyList(),
    val preview: GradeImportPreview? = null,
    val importedFileName: String? = null,
    val submitting: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
) {
    val totalWeight: Double get() = categories.sumOf { it.weightPercent }
    val schemeComplete: Boolean get() = categories.isNotEmpty() &&
        categories.all { it.name.isNotBlank() && it.weightPercent.isFinite() && it.weightPercent in 0.0..100.0 } &&
        categories.map { it.name.trim().lowercase() }.distinct().size == categories.size &&
        kotlin.math.abs(totalWeight - 100.0) <= 0.001
}

@HiltViewModel
class GradebookViewModel @Inject constructor(
    private val repository: GradebookRepository,
    private val classroomRepository: ClassroomRepository,
    private val tokenStore: SessionTokenStore,
) : ViewModel() {
    private val _state = MutableStateFlow(GradebookUiState())
    val state: StateFlow<GradebookUiState> = _state.asStateFlow()
    private var requestedClassroomId: String? = null

    fun requestClassroom(id: String) {
        if (_state.value.submitting) return
        requestedClassroomId = id
        if (!_state.value.loadingClassrooms) {
            if (_state.value.assignedClassrooms.any { it.id == id }) setClassroomId(id)
            else _state.update { it.copy(classroomId = "", categories = emptyList(), preview = null,
                importedFileName = null, errorMessage = "La clase solicitada ya no está asignada a tu cuenta.") }
            requestedClassroomId = null
        }
    }

    init { refreshClassrooms() }

    fun refreshClassrooms() {
        if (_state.value.submitting) return
        _state.update { it.copy(loadingClassrooms = true, classroomLoadError = null) }
        viewModelScope.launch {
            when (val result = classroomRepository.classrooms()) {
                is Outcome.Success -> {
                    val currentUserId = SessionTokenInspector.inspect(tokenStore.readAccessToken().orEmpty())?.userId
                    _state.update { current ->
                        val allowed = result.value.filter {
                            it.canManage && it.status == ClassroomStatus.ACTIVE && it.teacherId == currentUserId
                        }
                        val selection = if (requestedClassroomId != null) requestedClassroomId
                            ?.takeIf { id -> allowed.any { it.id == id } }.orEmpty()
                        else current.classroomId.takeIf { id -> allowed.any { it.id == id } }
                            ?: allowed.singleOrNull()?.id.orEmpty()
                        current.copy(
                            loadingClassrooms = false,
                            assignedClassrooms = allowed,
                            classroomId = selection,
                            classroomLoadError = null,
                            categories = if (selection == current.classroomId) current.categories else emptyList(),
                            preview = if (selection == current.classroomId) current.preview else null,
                            importedFileName = if (selection == current.classroomId) current.importedFileName else null,
                            gradingPeriod = if (selection == current.classroomId) current.gradingPeriod else "",
                        )
                    }
                    requestedClassroomId = null
                }
                is Outcome.Failure -> _state.update {
                    it.copy(
                        loadingClassrooms = false,
                        assignedClassrooms = emptyList(),
                        classroomId = "",
                        categories = emptyList(),
                        preview = null,
                        importedFileName = null,
                        gradingPeriod = "",
                        classroomLoadError = result.error.userMessage,
                    )
                }
            }
        }
    }

    fun setClassroomId(value: String) = _state.update { current ->
        if (current.submitting) return@update current
        if (!current.assignedClassrooms.any { it.id == value }) return@update current
        if (current.classroomId == value) return@update current
        // Do not carry one class's grade import or weighting into another class.
        current.copy(
            classroomId = value,
            categories = emptyList(),
            preview = null,
            importedFileName = null,
            gradingPeriod = "",
            errorMessage = null,
            successMessage = null,
        )
    }
    fun setGradingPeriod(value: String) = _state.update { if (it.submitting) it else it.copy(gradingPeriod = value.take(80)) }

    fun addCategory() = _state.update {
        if (it.submitting) it else it.copy(categories = it.categories + GradeCategoryDraft("", 0.0), errorMessage = null)
    }

    fun removeCategory(index: Int) = _state.update {
        if (it.submitting) it else it.copy(categories = it.categories.filterIndexed { position, _ -> position != index })
    }

    fun updateCategory(index: Int, name: String? = null, weight: Double? = null) = _state.update { current ->
        if (current.submitting) return@update current
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
        if (_state.value.submitting) return
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
                    it.copy(preview = null, importedFileName = null,
                        errorMessage = error.message ?: "No se pudo leer el archivo.", successMessage = null)
                }
            }
    }

    fun sync() {
        val current = _state.value
        if (current.submitting || current.loadingClassrooms) return
        if (!current.schemeComplete) {
            _state.update { it.copy(errorMessage = "La ponderación debe sumar exactamente 100%.") }
            return
        }
        if (current.assignedClassrooms.none { it.id == current.classroomId } || current.gradingPeriod.isBlank()) {
            _state.update { it.copy(errorMessage = "Selecciona una clase asignada y un periodo de evaluación.") }
            return
        }
        val preview = current.preview ?: run {
            _state.update { it.copy(errorMessage = "Importa un Excel/CSV o captura las calificaciones antes de sincronizar.") }
            return
        }
        if (preview.rows.isEmpty() || preview.warnings.isNotEmpty()) {
            _state.update { it.copy(errorMessage = "Corrige los errores del archivo antes de enviar las calificaciones.") }
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

        _state.update { it.copy(submitting = true, errorMessage = null, successMessage = null) }
        viewModelScope.launch {
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
                        successMessage = when (result.value.status) {
                            org.companerodeescuela.shared.contracts.GradeSyncStatus.SYNCED ->
                                "${result.value.acceptedStudentIds.size} calificaciones sincronizadas."
                            org.companerodeescuela.shared.contracts.GradeSyncStatus.PARTIALLY_SYNCED ->
                                "${result.value.acceptedStudentIds.size} aceptadas · ${result.value.rejectedStudentIds.size} rechazadas. Revisa el resultado."
                            org.companerodeescuela.shared.contracts.GradeSyncStatus.UNAVAILABLE -> null
                        },
                        errorMessage = if (result.value.status == org.companerodeescuela.shared.contracts.GradeSyncStatus.UNAVAILABLE)
                            "La sincronización institucional no está disponible." else null,
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
            val value = values.entries.firstOrNull { it.key.trim().equals(category.name.trim(), ignoreCase = true) }?.value
                ?: return null
            if (value !in 0.0..10.0) return null
            total += value * (category.weightPercent / 100.0)
        }
        return round(total * 100.0) / 100.0
    }
}
