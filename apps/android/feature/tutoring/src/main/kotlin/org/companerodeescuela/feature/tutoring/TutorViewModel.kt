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

data class TutorUiState(
    val loading: Boolean = true,
    val submitting: Boolean = false,
    val groups: List<AcademicGroupSummary> = emptyList(),
    val requests: List<ExcuseRequestSummary> = emptyList(),
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
                is Outcome.Success -> mutable.update { it.copy(loading = false, requests = result.value) }
                is Outcome.Failure -> mutable.update { it.copy(loading = false, requests = emptyList(), error = result.error.userMessage) }
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
