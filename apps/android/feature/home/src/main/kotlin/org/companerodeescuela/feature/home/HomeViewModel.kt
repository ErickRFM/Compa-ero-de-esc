package org.companerodeescuela.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.companerodeescuela.core.common.result.Outcome

data class HomeUiState(
    val loading: Boolean = true,
    val overview: TodayOverview? = null,
    val fromCache: Boolean = false,
    val updatedAtEpochSeconds: Long? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: AcademicHomeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, errorMessage = null)
            when (val result = repository.load()) {
                is Outcome.Success -> {
                    _state.value = HomeUiState(
                        loading = false,
                        overview = TodaySchedule.calculate(
                            load = result.value.academic,
                            now = LocalDateTime.now(),
                        ),
                        fromCache = result.value.fromCache,
                        updatedAtEpochSeconds = result.value.updatedAtEpochSeconds,
                    )
                }
                is Outcome.Failure -> {
                    _state.value = HomeUiState(
                        loading = false,
                        errorMessage = result.error.userMessage,
                    )
                }
            }
        }
    }
}
