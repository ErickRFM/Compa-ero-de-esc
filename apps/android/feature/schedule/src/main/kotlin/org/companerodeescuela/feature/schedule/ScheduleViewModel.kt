package org.companerodeescuela.feature.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.companerodeescuela.core.academic.AcademicRepository
import org.companerodeescuela.core.academic.PersonalScheduleDraft
import org.companerodeescuela.core.academic.PersonalScheduleRepository
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.shared.contracts.ScheduleEntry
import org.companerodeescuela.shared.contracts.ScheduleRecurrence
import org.companerodeescuela.shared.contracts.ScheduleSource

data class ScheduleUiState(
    val loading: Boolean = true,
    val actionInProgress: Boolean = false,
    val entries: List<ScheduleEntry> = emptyList(),
    val fromCache: Boolean = false,
    val importCandidates: List<PersonalScheduleDraft> = emptyList(),
    val successMessage: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val repository: AcademicRepository,
    private val personalRepository: PersonalScheduleRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ScheduleUiState())
    val state: StateFlow<ScheduleUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, errorMessage = null)
            when (val result = repository.load()) {
                is Outcome.Success -> {
                    _state.value = _state.value.copy(
                        loading = false,
                        entries = WeeklySchedule.order(result.value.academic.schedule.entries),
                        fromCache = result.value.fromCache,
                        errorMessage = null,
                    )
                }
                is Outcome.Failure -> {
                    _state.value = _state.value.copy(
                        loading = false,
                        errorMessage = result.error.userMessage,
                    )
                }
            }
        }
    }

    fun savePersonal(draft: PersonalScheduleDraft) {
        viewModelScope.launch {
            _state.value = _state.value.copy(actionInProgress = true, errorMessage = null)
            when (val result = personalRepository.save(draft.copy(source = ScheduleSource.MANUAL))) {
                is Outcome.Success -> {
                    _state.value = _state.value.copy(
                        actionInProgress = false,
                        successMessage = "Horario personal actualizado.",
                    )
                    load()
                }
                is Outcome.Failure -> {
                    _state.value = _state.value.copy(
                        actionInProgress = false,
                        errorMessage = result.error.userMessage,
                    )
                }
            }
        }
    }

    fun savePersonalDays(
        draft: PersonalScheduleDraft,
        days: Set<String>,
    ) {
        val normalizedDays = days.filter { it in academicDaysV8 }.toSet()
        if (normalizedDays.isEmpty()) {
            _state.value = _state.value.copy(errorMessage = "Selecciona al menos un día.")
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(actionInProgress = true, errorMessage = null)
            val seriesId = draft.seriesId ?: UUID.randomUUID().toString()
            var failure: Outcome.Failure? = null

            normalizedDays.forEachIndexed { index, day ->
                if (failure != null) return@forEachIndexed
                val candidate = draft.copy(
                    id = if (index == 0) draft.id else null,
                    dayOfWeek = day,
                    source = ScheduleSource.MANUAL,
                    recurrence = ScheduleRecurrence.WEEKLY,
                    seriesId = seriesId,
                    effectiveDate = null,
                )
                when (val result = personalRepository.save(candidate)) {
                    is Outcome.Success -> Unit
                    is Outcome.Failure -> failure = result
                }
            }

            if (failure == null) {
                _state.value = _state.value.copy(
                    actionInProgress = false,
                    successMessage = if (normalizedDays.size == 1) {
                        "Horario personal actualizado."
                    } else {
                        "Clase guardada en ${normalizedDays.size} días."
                    },
                )
                load()
            } else {
                _state.value = _state.value.copy(
                    actionInProgress = false,
                    errorMessage = failure?.error?.userMessage,
                )
            }
        }
    }

    fun movePersonal(proposal: AgendaMoveProposal) {
        val entry = proposal.entry
        if (entry.source == ScheduleSource.INSTITUTIONAL) return
        viewModelScope.launch {
            _state.value = _state.value.copy(actionInProgress = true, errorMessage = null)
            val draft = PersonalScheduleDraft(
                id = entry.courseId,
                subjectCode = entry.subjectCode,
                subjectName = entry.subjectName,
                groupName = entry.groupName,
                teacherName = entry.teacherName,
                dayOfWeek = proposal.targetDay,
                startsAt = proposal.targetStart,
                endsAt = proposal.targetEnd,
                classroomName = entry.classroomName,
                buildingName = entry.buildingName,
                source = entry.source,
                recurrence = entry.recurrence,
                seriesId = entry.seriesId,
                effectiveDate = entry.effectiveDate,
            )
            when (val result = personalRepository.save(draft)) {
                is Outcome.Success -> {
                    _state.value = _state.value.copy(
                        actionInProgress = false,
                        successMessage = "Clase movida a " +
                            dayShortLabelV8(proposal.targetDay) + " " +
                            proposal.targetStart + "–" + proposal.targetEnd + ".",
                    )
                    load()
                }
                is Outcome.Failure -> {
                    _state.value = _state.value.copy(
                        actionInProgress = false,
                        errorMessage = result.error.userMessage,
                    )
                }
            }
        }
    }

    fun deletePersonal(entry: ScheduleEntry) {
        if (entry.source == ScheduleSource.INSTITUTIONAL) return
        viewModelScope.launch {
            _state.value = _state.value.copy(actionInProgress = true, errorMessage = null)
            when (val result = personalRepository.delete(entry.courseId)) {
                is Outcome.Success -> {
                    _state.value = _state.value.copy(
                        actionInProgress = false,
                        successMessage = "Clase eliminada del horario personal.",
                    )
                    load()
                }
                is Outcome.Failure -> {
                    _state.value = _state.value.copy(
                        actionInProgress = false,
                        errorMessage = result.error.userMessage,
                    )
                }
            }
        }
    }

    fun stageImport(recognizedText: String) {
        val candidates = ScheduleOcrParser.parse(recognizedText)
        _state.value = _state.value.copy(
            importCandidates = candidates,
            errorMessage = if (candidates.isEmpty()) {
                "No pudimos detectar clases automáticamente. Puedes crear el horario manualmente."
            } else {
                null
            },
        )
    }

    fun updateImportCandidate(index: Int, draft: PersonalScheduleDraft) {
        val current = _state.value.importCandidates.toMutableList()
        if (index !in current.indices) return
        current[index] = draft.copy(source = ScheduleSource.OCR_IMPORT)
        _state.value = _state.value.copy(importCandidates = current)
    }

    fun discardImport() {
        _state.value = _state.value.copy(importCandidates = emptyList())
    }

    fun confirmImport() {
        val candidates = _state.value.importCandidates
        if (candidates.isEmpty()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(actionInProgress = true, errorMessage = null)
            when (val result = personalRepository.replace(ScheduleSource.OCR_IMPORT, candidates)) {
                is Outcome.Success -> {
                    _state.value = _state.value.copy(
                        actionInProgress = false,
                        importCandidates = emptyList(),
                        successMessage = "Horario importado y guardado en este dispositivo.",
                    )
                    load()
                }
                is Outcome.Failure -> {
                    _state.value = _state.value.copy(
                        actionInProgress = false,
                        errorMessage = result.error.userMessage,
                    )
                }
            }
        }
    }

    fun reportImportFailure() {
        _state.value = _state.value.copy(
            errorMessage = "No pudimos leer ese archivo. Prueba con una imagen más clara o crea el horario manualmente.",
        )
    }

    fun clearMessage() {
        _state.value = _state.value.copy(successMessage = null)
    }
}
