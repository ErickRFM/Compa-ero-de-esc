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
import kotlinx.coroutines.Job
import org.companerodeescuela.core.common.result.AppError
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.shared.contracts.AcademicGroupSummary
import org.companerodeescuela.shared.contracts.ExcuseRequestSummary
import org.companerodeescuela.shared.contracts.TutorStudentSummary
import org.companerodeescuela.shared.contracts.TutorCaseSummary
import org.companerodeescuela.shared.contracts.CreateTutorCaseRequest
import org.companerodeescuela.shared.contracts.AddTutorCaseNoteRequest
import org.companerodeescuela.shared.contracts.TutorNoteVisibility
import org.companerodeescuela.shared.contracts.ExcuseStatus

data class TutorUiState(
    val scopeGeneration: Long = 0,
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

    private var scopeGeneration = 0L
    private val operations = mutableSetOf<Job>()

    init {
        viewModelScope.launch {
            repository.observeScopeIdentity().collect { identity ->
                if (identity == null) invalidateScope(loading = false) else refresh()
            }
        }
    }

    fun refresh() {
        val generation = invalidateScope(loading = true)
        launchInScope(generation) {
            when (val result = repository.scope()) {
                is Outcome.Success -> updateInScope(generation) { it.copy(groups = result.value.groups.filter { group -> group.active }) }
                is Outcome.Failure -> {
                    failInScope(generation, result.error, revalidate = true)
                    return@launchInScope
                }
            }
            if (generation != scopeGeneration) return@launchInScope
            when (val result = repository.requests()) {
                is Outcome.Success -> updateInScope(generation) { state ->
                    state.copy(requests = result.value.filter { request -> state.groups.any { group -> group.id == request.academicGroupId } })
                }
                is Outcome.Failure -> {
                    failInScope(generation, result.error, revalidate = true)
                    return@launchInScope
                }
            }
            if (generation != scopeGeneration) return@launchInScope
            when (val result = repository.cases()) {
                is Outcome.Success -> updateInScope(generation) { state ->
                    state.copy(loading = false, cases = result.value.filter { record -> state.groups.any { group -> group.id == record.academicGroupId } })
                }
                is Outcome.Failure -> failInScope(generation, result.error, revalidate = true)
            }
        }
    }

    fun loadRoster(groupId: String) {
        val generation = scopeGeneration
        if (mutable.value.groups.none { it.id == groupId && it.active }) {
            failInScope(generation, AppError.Http(403))
            return
        }
        launchInScope(generation) {
            updateInScope(generation) {
                it.copy(rosterGroupId = groupId, roster = emptyList(), rosterLoading = true, error = null)
            }
            when (val result = repository.students(groupId)) {
                is Outcome.Success -> updateInScope(generation) {
                    if (it.rosterGroupId != groupId) it else it.copy(roster = result.value.filter { student -> student.academicGroupId == groupId }, rosterLoading = false)
                }
                is Outcome.Failure -> failInScope(generation, result.error)
            }
        }
    }

    fun createCase(groupId: String, studentId: String, summary: String, expectedScopeGeneration: Long = scopeGeneration) {
        if (expectedScopeGeneration != scopeGeneration || mutable.value.loading || mutable.value.submitting) return
        val generation = scopeGeneration
        if (mutable.value.groups.none { it.id == groupId && it.active }) {
            failInScope(generation, AppError.Http(403))
            return
        }
        launchInScope(generation) {
            updateInScope(generation) { it.copy(submitting = true, error = null, notice = null) }
            when (val result = repository.createCase(CreateTutorCaseRequest(groupId, studentId, summary))) {
                is Outcome.Success -> {
                    updateInScope(generation) { it.copy(submitting = false, notice = "Seguimiento creado.") }
                    if (generation != scopeGeneration) return@launchInScope
                    when (val updated = repository.cases()) {
                        is Outcome.Success -> updateInScope(generation) { state ->
                            state.copy(cases = updated.value.filter { record -> state.groups.any { group -> group.id == record.academicGroupId } })
                        }
                        is Outcome.Failure -> failInScope(generation, updated.error)
                    }
                }
                is Outcome.Failure -> failInScope(generation, result.error)
            }
        }
    }

    fun addNote(caseId: String, body: String, publish: Boolean, expectedScopeGeneration: Long = scopeGeneration) {
        if (expectedScopeGeneration != scopeGeneration || mutable.value.loading || mutable.value.submitting) return
        val generation = scopeGeneration
        if (mutable.value.cases.none { it.id == caseId && mutable.value.groups.any { group -> group.id == it.academicGroupId } }) {
            failInScope(generation, AppError.Http(403))
            return
        }
        launchInScope(generation) {
            updateInScope(generation) { it.copy(submitting = true, error = null, notice = null) }
            val visibility = if (publish) TutorNoteVisibility.STUDENT_VISIBLE else TutorNoteVisibility.TUTOR_INTERNAL
            when (val result = repository.addNote(caseId, AddTutorCaseNoteRequest(body, visibility))) {
                is Outcome.Success -> updateInScope(generation) {
                    it.copy(submitting = false, notice = "Observación guardada.",
                        cases = it.cases.map { record -> if (record.id == caseId) result.value else record })
                }
                is Outcome.Failure -> failInScope(generation, result.error)
            }
        }
    }

    fun review(id: String, approved: Boolean, comment: String?, expectedScopeGeneration: Long = scopeGeneration) {
        if (expectedScopeGeneration != scopeGeneration || mutable.value.loading || mutable.value.submitting) return
        val generation = scopeGeneration
        val current = mutable.value
        if (current.requests.none { request ->
                request.id == id && request.status in setOf(ExcuseStatus.PENDING, ExcuseStatus.UNDER_REVIEW) &&
                    current.groups.any { it.id == request.academicGroupId && it.active }
            }) {
            failInScope(generation, AppError.Http(403))
            return
        }
        launchInScope(generation) {
            updateInScope(generation) { it.copy(submitting = true, error = null, notice = null) }
            when (val result = repository.review(id, approved, comment)) {
                is Outcome.Success -> {
                    if (generation == scopeGeneration) refresh()
                }
                is Outcome.Failure -> failInScope(generation, result.error)
            }
        }
    }

    private fun invalidateScope(loading: Boolean, error: String? = null): Long {
        scopeGeneration++
        operations.toList().forEach { it.cancel() }
        operations.clear()
        mutable.value = TutorUiState(scopeGeneration = scopeGeneration, loading = loading, error = error)
        return scopeGeneration
    }

    private fun launchInScope(generation: Long, block: suspend () -> Unit) {
        if (generation != scopeGeneration) return
        val job = viewModelScope.launch { block() }
        operations.add(job)
        job.invokeOnCompletion { operations.remove(job) }
    }

    private fun updateInScope(generation: Long, transform: (TutorUiState) -> TutorUiState) {
        if (generation == scopeGeneration) mutable.update(transform)
    }

    private fun failInScope(generation: Long, error: AppError, revalidate: Boolean = false) {
        if (generation != scopeGeneration) return
        if (revalidate || error is AppError.Http && error.status in setOf(401, 403)) {
            invalidateScope(loading = false, error = error.userMessage)
        } else updateInScope(generation) {
            it.copy(loading = false, submitting = false, rosterLoading = false, error = error.userMessage)
        }
    }
}
