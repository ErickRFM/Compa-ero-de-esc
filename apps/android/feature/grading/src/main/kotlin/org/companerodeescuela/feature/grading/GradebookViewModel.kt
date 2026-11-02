package org.companerodeescuela.feature.grading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
import org.companerodeescuela.shared.contracts.GradebookCategory
import org.companerodeescuela.shared.contracts.GradebookDraftRequest
import org.companerodeescuela.shared.contracts.GradebookPublishRequest
import org.companerodeescuela.shared.contracts.GradebookRow
import org.companerodeescuela.shared.contracts.GradebookStatus
import java.util.UUID
import javax.inject.Inject
import kotlin.math.round

data class GradebookUiState(
    val classroomId: String = "",
    val assignedClassrooms: List<ClassroomSummary> = emptyList(),
    val loadingClassrooms: Boolean = true,
    val classroomLoadError: String? = null,
    val gradingPeriod: String = "",
    val categories: List<GradeCategoryDraft> = emptyList(),
    val categoryIds: List<String> = emptyList(),
    val preview: GradeImportPreview? = null,
    val importedFileName: String? = null,
    val submitting: Boolean = false,
    val gradebookRevision: Long? = null,
    val gradebookPublished: Boolean = false,
    val draftHasUnsavedChanges: Boolean = false,
    val gradebookLoading: Boolean = false,
    val capabilityLoading: Boolean = true,
    val institutionalSyncAvailable: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
) {
    val totalWeight: Double get() = categories.sumOf { it.weightPercent }
    val schemeComplete: Boolean get() =
        categories.isNotEmpty() &&
            categories.all { it.name.isNotBlank() && it.weightPercent.isFinite() && it.weightPercent in 0.0..100.0 } &&
            categories.map { it.name.trim().lowercase() }.distinct().size == categories.size &&
            kotlin.math.abs(totalWeight - 100.0) <= 0.001
}

@HiltViewModel
class GradebookViewModel
    @Inject
    constructor(
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
                if (_state.value.assignedClassrooms.any { it.id == id }) {
                    setClassroomId(id)
                } else {
                    _state.update {
                        it.copy(
                            classroomId = "",
                            categories = emptyList(),
                            categoryIds = emptyList(),
                            gradebookRevision = null,
                            gradebookPublished = false,
                            draftHasUnsavedChanges = false,
                            gradebookLoading = false,
                            preview = null,
                            importedFileName = null,
                            errorMessage = "La clase solicitada ya no está asignada a tu cuenta.",
                        )
                    }
                }
                requestedClassroomId = null
            }
        }

        init {
            refreshClassrooms()
            refreshCapability()
        }

        fun refreshCapability() {
            if (_state.value.submitting) return
            _state.update {
                it.copy(capabilityLoading = true, institutionalSyncAvailable = false)
            }
            viewModelScope.launch {
                when (val result = repository.capability()) {
                    is Outcome.Success ->
                        _state.update {
                            it.copy(capabilityLoading = false, institutionalSyncAvailable = result.value.available)
                        }
                    is Outcome.Failure ->
                        _state.update {
                            // Unknown readiness must never be rendered as an enabled publish action.
                            it.copy(capabilityLoading = false, institutionalSyncAvailable = false)
                        }
                }
            }
        }

        fun refreshClassrooms() {
            if (_state.value.submitting) return
            _state.update { it.copy(loadingClassrooms = true, classroomLoadError = null) }
            viewModelScope.launch {
                when (val result = classroomRepository.classrooms()) {
                    is Outcome.Success -> {
                        val currentUserId = SessionTokenInspector.inspect(tokenStore.readAccessToken().orEmpty())?.userId
                        _state.update { current ->
                            val allowed =
                                result.value.filter {
                                    it.canManage && it.status == ClassroomStatus.ACTIVE && it.teacherId == currentUserId
                                }
                            val selection =
                                if (requestedClassroomId != null) {
                                    requestedClassroomId
                                        ?.takeIf { id -> allowed.any { it.id == id } }
                                        .orEmpty()
                                } else {
                                    current.classroomId.takeIf { id -> allowed.any { it.id == id } }
                                        ?: allowed.singleOrNull()?.id.orEmpty()
                                }
                            current.copy(
                                loadingClassrooms = false,
                                assignedClassrooms = allowed,
                                classroomId = selection,
                                classroomLoadError = null,
                                categories = if (selection == current.classroomId) current.categories else emptyList(),
                                categoryIds = if (selection == current.classroomId) current.categoryIds else emptyList(),
                                gradebookRevision = if (selection == current.classroomId) current.gradebookRevision else null,
                                gradebookPublished = selection == current.classroomId && current.gradebookPublished,
                                draftHasUnsavedChanges = selection == current.classroomId && current.draftHasUnsavedChanges,
                                gradebookLoading = selection == current.classroomId && current.gradebookLoading,
                                preview = if (selection == current.classroomId) current.preview else null,
                                importedFileName = if (selection == current.classroomId) current.importedFileName else null,
                                gradingPeriod = if (selection == current.classroomId) current.gradingPeriod else "",
                            )
                        }
                        requestedClassroomId = null
                    }
                    is Outcome.Failure ->
                        _state.update {
                            it.copy(
                                loadingClassrooms = false,
                                assignedClassrooms = emptyList(),
                                classroomId = "",
                                categories = emptyList(),
                                categoryIds = emptyList(),
                                gradebookRevision = null,
                                gradebookPublished = false,
                                draftHasUnsavedChanges = false,
                                gradebookLoading = false,
                                preview = null,
                                importedFileName = null,
                                gradingPeriod = "",
                                classroomLoadError = result.error.userMessage,
                            )
                        }
                }
            }
        }

        fun setClassroomId(value: String) =
            _state.update { current ->
                if (current.submitting) return@update current
                if (!current.assignedClassrooms.any { it.id == value }) return@update current
                if (current.classroomId == value) return@update current
                // Do not carry one class's grade import or weighting into another class.
                current.copy(
                    classroomId = value,
                    categories = emptyList(),
                    categoryIds = emptyList(),
                    gradebookRevision = null,
                    gradebookPublished = false,
                    draftHasUnsavedChanges = false,
                    gradebookLoading = false,
                    preview = null,
                    importedFileName = null,
                    gradingPeriod = "",
                    errorMessage = null,
                    successMessage = null,
                )
            }

        fun setGradingPeriod(value: String) {
            val normalized = value.take(80)
            _state.update {
                if (it.submitting) {
                    it
                } else {
                    it.copy(
                        gradingPeriod = normalized,
                        categories = emptyList(),
                        categoryIds = emptyList(),
                        preview = null,
                        importedFileName = null,
                        gradebookRevision = null,
                        gradebookPublished = false,
                        draftHasUnsavedChanges = false,
                        gradebookLoading = false,
                    )
                }
            }
            loadGradebook()
        }

        fun addCategory() =
            _state.update {
                if (it.submitting || it.gradebookPublished) {
                    it
                } else {
                    it.copy(
                        categories = it.categories + GradeCategoryDraft("", 0.0),
                        categoryIds = it.categoryIds + UUID.randomUUID().toString(),
                        draftHasUnsavedChanges = true,
                        errorMessage = null,
                    )
                }
            }

        fun removeCategory(index: Int) =
            _state.update {
                if (it.submitting || it.gradebookPublished) {
                    it
                } else {
                    it.copy(
                        categories = it.categories.filterIndexed { position, _ -> position != index },
                        categoryIds = it.categoryIds.filterIndexed { position, _ -> position != index },
                        draftHasUnsavedChanges = true,
                    )
                }
            }

        fun updateCategory(
            index: Int,
            name: String? = null,
            weight: Double? = null,
        ) = _state.update { current ->
            if (current.submitting || current.gradebookPublished) return@update current
            val categories =
                current.categories.mapIndexed { position, category ->
                    if (position != index) {
                        category
                    } else {
                        category.copy(
                            name = name?.take(80) ?: category.name,
                            weightPercent = weight ?: category.weightPercent,
                        )
                    }
                }
            current.copy(
                categories = categories,
                draftHasUnsavedChanges = current.draftHasUnsavedChanges || categories != current.categories,
                errorMessage = null,
            )
        }

        fun importSpreadsheet(
            fileName: String,
            bytes: ByteArray,
            expectedClassroomId: String = _state.value.classroomId,
            expectedGradingPeriod: String = _state.value.gradingPeriod,
        ) {
            val current = _state.value
            if (current.submitting ||
                current.gradebookPublished ||
                current.classroomId != expectedClassroomId ||
                current.gradingPeriod != expectedGradingPeriod
            ) {
                return
            }
            runCatching { SpreadsheetReader.read(fileName, bytes) }
                .onSuccess { preview ->
                    _state.update {
                        if (it.submitting ||
                            it.gradebookPublished ||
                            it.classroomId != expectedClassroomId ||
                            it.gradingPeriod != expectedGradingPeriod
                        ) {
                            return@update it
                        }
                        it.copy(
                            preview = preview,
                            importedFileName = fileName,
                            draftHasUnsavedChanges = true,
                            errorMessage = null,
                            successMessage = "${preview.rows.size} alumnos leídos. Revisa antes de sincronizar.",
                        )
                    }
                }.onFailure { error ->
                    reportImportFailure(error.message ?: "No se pudo leer el archivo.", expectedClassroomId, expectedGradingPeriod)
                }
        }

        fun reportImportFailure(
            message: String,
            expectedClassroomId: String,
            expectedGradingPeriod: String,
        ) = _state.update {
            if (it.submitting ||
                it.gradebookPublished ||
                it.classroomId != expectedClassroomId ||
                it.gradingPeriod != expectedGradingPeriod
            ) {
                return@update it
            }
            it.copy(
                preview = null,
                importedFileName = null,
                draftHasUnsavedChanges = true,
                errorMessage = message,
                successMessage = null,
            )
        }

        fun saveDraft() {
            val current = _state.value
            if (current.submitting || current.gradebookLoading || current.gradebookPublished) return
            val rows = draftRows(current) ?: return
            val request =
                GradebookDraftRequest(
                    classroomId = current.classroomId,
                    gradingPeriod = current.gradingPeriod,
                    expectedRevision = current.gradebookRevision,
                    categories =
                        current.categories.mapIndexed { index, category ->
                            GradebookCategory(
                                current.categoryIds.getOrElse(index) { "category-${index + 1}" },
                                category.name.trim(),
                                category.weightPercent,
                            )
                        },
                    rows = rows,
                )
            _state.update { it.copy(submitting = true, errorMessage = null, successMessage = null) }
            viewModelScope.launch {
                when (val result = repository.saveDraft(request)) {
                    is Outcome.Success ->
                        _state.update {
                            it.copy(
                                submitting = false,
                                gradebookRevision = result.value.revision,
                                gradebookPublished = result.value.status == GradebookStatus.PUBLISHED,
                                draftHasUnsavedChanges = false,
                                successMessage = "Borrador guardado · revisión ${result.value.revision}.",
                            )
                        }
                    is Outcome.Failure -> _state.update { it.copy(submitting = false, errorMessage = result.error.userMessage) }
                }
            }
        }

        fun publishDraft() {
            val current = _state.value
            val revision = current.gradebookRevision
            if (current.submitting || current.gradebookLoading || revision == null || current.gradebookPublished) return
            if (current.draftHasUnsavedChanges) {
                _state.update { it.copy(errorMessage = "Guarda los cambios del borrador antes de publicar.") }
                return
            }
            _state.update { it.copy(submitting = true, errorMessage = null, successMessage = null) }
            viewModelScope.launch {
                when (val result = repository.publish(GradebookPublishRequest(current.classroomId, current.gradingPeriod, revision))) {
                    is Outcome.Success ->
                        _state.update {
                            it.copy(
                                submitting = false,
                                gradebookRevision = result.value.revision,
                                gradebookPublished = true,
                                successMessage = "Calificaciones publicadas para ${result.value.rows.size} estudiantes.",
                            )
                        }
                    is Outcome.Failure -> _state.update { it.copy(submitting = false, errorMessage = result.error.userMessage) }
                }
            }
        }

        private fun loadGradebook() {
            val current = _state.value
            if (current.classroomId.isBlank() || current.gradingPeriod.isBlank() || current.submitting) return
            _state.update { it.copy(gradebookLoading = true) }
            viewModelScope.launch {
                when (val result = repository.gradebook(current.classroomId, current.gradingPeriod)) {
                    is Outcome.Success ->
                        _state.update { state ->
                            if (state.classroomId != current.classroomId ||
                                state.gradingPeriod != current.gradingPeriod
                            ) {
                                return@update state
                            }
                            val snapshot = result.value ?: return@update state.copy(gradebookLoading = false)
                            if (state.classroomId != snapshot.classroomId || state.gradingPeriod != snapshot.gradingPeriod) {
                                return@update state.copy(gradebookLoading = false)
                            }
                            if (snapshot.revision < (state.gradebookRevision ?: 0L) ||
                                state.gradebookPublished &&
                                snapshot.status != GradebookStatus.PUBLISHED
                            ) {
                                return@update state.copy(gradebookLoading = false)
                            }
                            if (state.draftHasUnsavedChanges && snapshot.status == GradebookStatus.DRAFT) {
                                return@update state.copy(gradebookLoading = false, gradebookRevision = snapshot.revision)
                            }
                            val names = snapshot.categories.associate { it.id to it.name }
                            state.copy(
                                categories = snapshot.categories.map { GradeCategoryDraft(it.name, it.weightPercent) },
                                categoryIds = snapshot.categories.map { it.id },
                                preview =
                                    org.companerodeescuela.shared.contracts.GradeImportPreview(
                                        columns = snapshot.categories.map { it.name },
                                        rows =
                                            snapshot.rows.map { row ->
                                                org.companerodeescuela.shared.contracts.GradeImportRow(
                                                    studentId = row.studentId,
                                                    values =
                                                        row.scores
                                                            .mapNotNull { (id, score) ->
                                                                names[id]?.let { it to score }
                                                            }.toMap(),
                                                )
                                            },
                                    ),
                                gradebookRevision = snapshot.revision,
                                gradebookPublished = snapshot.status == GradebookStatus.PUBLISHED,
                                draftHasUnsavedChanges = false,
                                gradebookLoading = false,
                                importedFileName = null,
                                successMessage = null,
                                errorMessage =
                                    if (state.draftHasUnsavedChanges && snapshot.status == GradebookStatus.PUBLISHED) {
                                        "El periodo ya está publicado. Se muestran las calificaciones guardadas; no se aplicaron las ediciones locales."
                                    } else {
                                        null
                                    },
                            )
                        }
                    is Outcome.Failure ->
                        _state.update {
                            if (it.classroomId == current.classroomId && it.gradingPeriod == current.gradingPeriod) {
                                it.copy(gradebookLoading = false)
                            } else {
                                it
                            }
                        }
                }
            }
        }

        private fun draftRows(current: GradebookUiState): List<GradebookRow>? {
            if (!current.schemeComplete) {
                _state.update { it.copy(errorMessage = "La ponderación debe sumar exactamente 100%.") }
                return null
            }
            if (current.classroomId.isBlank() ||
                current.gradingPeriod.isBlank() ||
                current.assignedClassrooms.none { it.id == current.classroomId }
            ) {
                _state.update { it.copy(errorMessage = "Selecciona una clase asignada y un periodo de evaluación.") }
                return null
            }
            val preview = current.preview
            if (preview == null || preview.rows.isEmpty() || preview.warnings.isNotEmpty()) {
                _state.update { it.copy(errorMessage = "Importa y revisa un archivo válido antes de guardar el borrador.") }
                return null
            }
            return preview.rows.map { student ->
                GradebookRow(
                    studentId = student.studentId,
                    scores =
                        current.categories
                            .mapIndexed { index, category ->
                                val id = current.categoryIds.getOrElse(index) { "category-${index + 1}" }
                                id to
                                    student.values.entries
                                        .firstOrNull { it.key.trim().equals(category.name.trim(), ignoreCase = true) }
                                        ?.value
                            }.toMap(),
                )
            }
        }

        fun sync() {
            val current = _state.value
            if (current.submitting || current.loadingClassrooms || current.gradebookLoading) return
            if (current.capabilityLoading || !current.institutionalSyncAvailable) {
                _state.update {
                    it.copy(
                        errorMessage =
                            "La publicación institucional no está configurada. Puedes importar y revisar, pero no enviar calificaciones.",
                    )
                }
                return
            }
            if (!current.schemeComplete) {
                _state.update { it.copy(errorMessage = "La ponderación debe sumar exactamente 100%.") }
                return
            }
            if (current.assignedClassrooms.none { it.id == current.classroomId } || current.gradingPeriod.isBlank()) {
                _state.update { it.copy(errorMessage = "Selecciona una clase asignada y un periodo de evaluación.") }
                return
            }
            val preview =
                current.preview ?: run {
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
                if (finalGrade == null) {
                    missing += student.studentId
                } else {
                    rows += GradeSyncRow(studentId = student.studentId, finalGrade = finalGrade)
                }
            }
            if (missing.isNotEmpty()) {
                _state.update {
                    it.copy(
                        errorMessage =
                            "Faltan valores válidos para ${missing.size} alumnos. Verifica que los nombres de las columnas coincidan con las categorías.",
                    )
                }
                return
            }

            _state.update { it.copy(submitting = true, errorMessage = null, successMessage = null) }
            viewModelScope.launch {
                when (
                    val result =
                        repository.sync(
                            GradeSyncRequest(
                                classroomId = current.classroomId,
                                gradingPeriod = current.gradingPeriod,
                                rows = rows,
                            ),
                        )
                ) {
                    is Outcome.Success ->
                        _state.update {
                            it.copy(
                                submitting = false,
                                successMessage =
                                    when (result.value.status) {
                                        org.companerodeescuela.shared.contracts.GradeSyncStatus.SYNCED ->
                                            "${result.value.acceptedStudentIds.size} calificaciones sincronizadas."
                                        org.companerodeescuela.shared.contracts.GradeSyncStatus.PARTIALLY_SYNCED ->
                                            "${result.value.acceptedStudentIds.size} aceptadas · ${result.value.rejectedStudentIds.size} rechazadas. Revisa el resultado."
                                        org.companerodeescuela.shared.contracts.GradeSyncStatus.UNAVAILABLE -> null
                                    },
                                errorMessage =
                                    if (result.value.status == org.companerodeescuela.shared.contracts.GradeSyncStatus.UNAVAILABLE) {
                                        "La sincronización institucional no está disponible."
                                    } else {
                                        null
                                    },
                            )
                        }
                    is Outcome.Failure ->
                        _state.update {
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
                val value =
                    values.entries.firstOrNull { it.key.trim().equals(category.name.trim(), ignoreCase = true) }?.value
                        ?: return null
                if (value !in 0.0..10.0) return null
                total += value * (category.weightPercent / 100.0)
            }
            return round(total * 100.0) / 100.0
        }
    }
