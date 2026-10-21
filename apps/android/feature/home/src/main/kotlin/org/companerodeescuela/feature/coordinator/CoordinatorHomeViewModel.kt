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
import org.companerodeescuela.core.common.result.AppError
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.shared.contracts.CoordinationOverview

data class CoordinatorOverviewUiState(
    val loading: Boolean = true,
    val overview: CoordinationOverview? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class CoordinatorHomeViewModel
    @Inject
    constructor(
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
                        _state.update { old ->
                            // Only transient failures may retain the last authorized snapshot.
                            // 401/403 and other permission/contract failures must invalidate it.
                            old.copy(
                                loading = false,
                                overview = old.overview.takeIf { retainCoordinationSnapshot(result.error) },
                                errorMessage = result.error.userMessage,
                            )
                        }
                }
            }
    }
}

internal fun retainCoordinationSnapshot(error: AppError): Boolean =
    when (error) {
        is AppError.Network -> true
        is AppError.Http -> error.status == 429 || error.status in 500..599
        else -> false
    }
