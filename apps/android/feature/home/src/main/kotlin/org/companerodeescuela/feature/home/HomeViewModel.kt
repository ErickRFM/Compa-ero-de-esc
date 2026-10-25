package org.companerodeescuela.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.companerodeescuela.core.academic.AcademicRepository
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.shared.contracts.AcademicLoadResponse
import java.time.LocalDateTime
import javax.inject.Inject

data class HomeUiState(
    val loading: Boolean = true,
    val overview: TodayOverview? = null,
    val fromCache: Boolean = false,
    val updatedAtEpochSeconds: Long? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val repository: AcademicRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(HomeUiState())
        val state: StateFlow<HomeUiState> = _state.asStateFlow()
        private var latestAcademic: AcademicLoadResponse? = null
        private var refreshJob: Job? = null

        init {
            refresh()
            // Recalculate from the last authorized snapshot without requesting the network
            // every minute. This also rolls the displayed weekday over at midnight.
            viewModelScope.launch {
                while (isActive) {
                    delay(60_000L)
                    val snapshot = latestAcademic ?: continue
                    _state.update { state ->
                        state.copy(overview = TodaySchedule.calculate(snapshot, LocalDateTime.now()))
                    }
                }
            }
        }

        fun refresh() {
            // Both init and ON_RESUME may request refresh during the first composition.
            if (refreshJob?.isActive == true) return
            refreshJob =
                viewModelScope.launch {
                    _state.update { it.copy(loading = true, errorMessage = null) }
                    when (val result = repository.load()) {
                        is Outcome.Success -> {
                            latestAcademic = result.value.academic
                            _state.value =
                                HomeUiState(
                                    loading = false,
                                    overview = TodaySchedule.calculate(result.value.academic, LocalDateTime.now()),
                                    fromCache = result.value.fromCache,
                                    updatedAtEpochSeconds = result.value.updatedAtEpochSeconds,
                                )
                        }
                        is Outcome.Failure -> {
                            latestAcademic = null
                            _state.update {
                                HomeUiState(
                                    loading = false,
                                    errorMessage = result.error.userMessage,
                                )
                            }
                        }
                    }
                }
        }
    }
