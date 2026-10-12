package org.companerodeescuela.feature.coordinator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
): ViewModel() {
    private val _state = MutableStateFlow(CoordinatorOverviewUiState())
    val state: StateFlow<CoordinatorOverviewUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        if (_state.value.loading && _state.value.overview != null) return
        _state.value = CoordinatorOverviewUiState(loading = true)
        viewModelScope.launch {
            when (val result = repository.overview()) {
                is Outcome.Success -> _state.value = CoordinatorOverviewUiState(
                    loading = false, overview = result.value,
                )
                is Outcome.Failure -> _state.value = CoordinatorOverviewUiState(
                    loading = false, errorMessage = result.error.userMessage,
                )
            }
        }
    }
}
