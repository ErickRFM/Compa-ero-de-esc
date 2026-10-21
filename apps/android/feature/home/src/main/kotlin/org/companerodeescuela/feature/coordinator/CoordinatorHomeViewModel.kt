package org.companerodeescuela.feature.coordinator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.shared.contracts.CoordinationOverview

data class CoordinatorOverviewUiState(
    val loading: Boolean = true,
    val overview: CoordinationOverview? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class CoordinatorHomeViewModel @Inject constructor(
    private val repository: CoordinatorRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CoordinatorOverviewUiState())
    val state: StateFlow<CoordinatorOverviewUiState> = _state.asStateFlow()
    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        // init and ON_RESUME can occur together; avoid duplicate network calls.
        if (refreshJob?.isActive == true) return
        refreshJob =
            viewModelScope.launch {
                _state.update { it.copy(loading = true, errorMessage = null) }
                when (val result = repository.overview()) {
                    is Outcome.Success ->
                        _state.value =
                            CoordinatorOverviewUiState(
                                loading = false,
                                overview = result.value,
                            )
                    is Outcome.Failure ->
                        _state.update {
                            // Keep the last authorized institutional overview on transient
                            // failure, but label it as potentially outdated in the UI.
                            it.copy(loading = false, errorMessage = result.error.userMessage)
                        }
                }
            }
    }
}
