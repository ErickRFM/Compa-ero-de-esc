package org.companerodeescuela.feature.tutoring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.shared.contracts.AcademicGroupSummary
import org.companerodeescuela.shared.contracts.ExcuseRequestSummary
import org.companerodeescuela.shared.contracts.TutorStudentSummary
import org.companerodeescuela.shared.contracts.TutorCaseSummary
import org.companerodeescuela.shared.contracts.CreateTutorCaseRequest
import org.companerodeescuela.shared.contracts.AddTutorCaseNoteRequest
import org.companerodeescuela.shared.contracts.TutorNoteVisibility

data class TutorUiState(
    val loading: Boolean = true,
    val submitting: Boolean = false,
    val groups: List<AcademicGroupSummary> = emptyList(),
    val requests: List<ExcuseRequestSummary> = emptyList(),
    val roster: List<TutorStudentSummary> = emptyList(),
    val rosterGroupId: String? = null,
    val rosterLoading: Boolean = false,
    val cases: List<TutorCaseSummary> = emptyList(),
    val error: String? = null,
    val notice: String? = null,
)

@HiltViewModel
class TutorViewModel @Inject constructor(private val repository: TutorRepository) : ViewModel() {
    private val mutable = MutableStateFlow(TutorUiState())
    val state: StateFlow<TutorUiState> = mutable.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            mutable.update { it.copy(loading = true, error = null) }
            when (val result = repository.scope()) {
                is Outcome.Success -> mutable.update { it.copy(groups = result.value.groups) }
                is Outcome.Failure -> {
                    mutable.update { it.copy(loading = false, groups = emptyList(), requests = emptyList(), error = result.error.userMessage) }
                    return@launch
                }
            }
            when (val result = repository.requests()) {
                is Outcome.Success -> mutable.update { it.copy(requests = result.value) }
                is Outcome.Failure -> mutable.update { it.copy(requests = emptyList(), error = result.error.userMessage) }
            }
            when (val result = repository.cases()) {
                is Outcome.Success -> mutable.update { it.copy(loading = false, cases = result.value) }
                is Outcome.Failure -> mutable.update { it.copy(loading = false, cases = emptyList(), error = result.error.userMessage) }
            }
        }
    }

    fun loadRoster(groupId: String) {
        viewModelScope.launch {
            mutable.update {
                it.copy(rosterGroupId = groupId, roster = emptyList(), rosterLoading = true, error = null)
            }
            when (val result = repository.students(groupId)) {
                is Outcome.Success -> mutable.update {
                    if (it.rosterGroupId != groupId) it else it.copy(roster = result.value, rosterLoading = false)
                }
                is Outcome.Failure -> mutable.update {
                    if (it.rosterGroupId != groupId) it else it.copy(rosterLoading = false, error = result.error.userMessage)
                }
            }
        }
    }

    fun createCase(groupId: String, studentId: String, summary: String) {
        if (mutable.value.submitting) return
        viewModelScope.launch {
            mutable.update { it.copy(submitting = true, error = null, notice = null) }
            when (val result = repository.createCase(CreateTutorCaseRequest(groupId, studentId, summary))) {
                is Outcome.Success -> {
                    mutable.update { it.copy(submitting = false, notice = "Seguimiento creado.") }
                    when (val updated = repository.cases()) {
                        is Outcome.Success -> mutable.update { it.copy(cases = updated.value) }
                        is Outcome.Failure -> mutable.update { it.copy(error = updated.error.userMessage) }
                    }
                }
                is Outcome.Failure -> mutable.update { it.copy(submitting = false, error = result.error.userMessage) }
            }
        }
    }

    fun addNote(caseId: String, body: String, publish: Boolean) {
        if (mutable.value.submitting) return
        viewModelScope.launch {
            mutable.update { it.copy(submitting = true, error = null, notice = null) }
            val visibility = if (publish) TutorNoteVisibility.STUDENT_VISIBLE else TutorNoteVisibility.TUTOR_INTERNAL
            when (val result = repository.addNote(caseId, AddTutorCaseNoteRequest(body, visibility))) {
                is Outcome.Success -> mutable.update {
                    it.copy(submitting = false, notice = "Observación guardada.",
                        cases = it.cases.map { record -> if (record.id == caseId) result.value else record })
                }
                is Outcome.Failure -> mutable.update { it.copy(submitting = false, error = result.error.userMessage) }
            }
        }
    }

    fun review(id: String, approved: Boolean, comment: String?) {
        if (mutable.value.submitting) return
        viewModelScope.launch {
            mutable.update { it.copy(submitting = true, error = null, notice = null) }
            when (val result = repository.review(id, approved, comment)) {
                is Outcome.Success -> {
                    mutable.update { it.copy(submitting = false, notice = "Solicitud revisada.") }
                    refresh()
                }
                is Outcome.Failure -> mutable.update { it.copy(submitting = false, error = result.error.userMessage) }
            }
        }
    }
}
