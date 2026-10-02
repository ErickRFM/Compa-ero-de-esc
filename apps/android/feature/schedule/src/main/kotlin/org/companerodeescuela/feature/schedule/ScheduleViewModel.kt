package org.companerodeescuela.feature.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.companerodeescuela.core.academic.AcademicRepository
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.shared.contracts.ScheduleEntry

data class ScheduleUiState(
    val loading: Boolean = true,
    val entries: List<ScheduleEntry> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val repository: AcademicRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ScheduleUiState())
    val state: StateFlow<ScheduleUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            when (val result = repository.readWeeklySchedule()) {
                is Outcome.Success -> {
                    _state.value = ScheduleUiState(
                        loading = false,
                        entries = WeeklySchedule.order(result.value),
                    )
                }
                is Outcome.Failure -> {
                    _state.value = ScheduleUiState(
                        loading = false,
                        errorMessage = result.error.userMessage,
                    )
                }
            }
        }
    }
}
