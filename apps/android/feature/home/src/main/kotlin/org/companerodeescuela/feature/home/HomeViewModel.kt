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
import org.companerodeescuela.core.academic.PersonalScheduleRepository
import org.companerodeescuela.core.common.result.AppError
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.shared.contracts.AcademicLoadResponse
import org.companerodeescuela.shared.contracts.ScheduleEntry
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
        private val personalRepository: PersonalScheduleRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(HomeUiState())
        val state: StateFlow<HomeUiState> = _state.asStateFlow()
        private var latestAcademic: AcademicLoadResponse? = null
        private var latestPersonal: List<ScheduleEntry>? = null
        private var refreshJob: Job? = null

        init {
            refresh()
            // Recalculate the current day from the last authorized source, even offline.
            viewModelScope.launch {
                while (isActive) {
                    delay(60_000L)
                    val snapshot = latestAcademic
                    val personal = latestPersonal
                    _state.update { state ->
                        state.copy(
                            overview =
                                when {
                                    snapshot != null -> TodaySchedule.calculate(snapshot, LocalDateTime.now())
                                    personal != null -> TodaySchedule.calculateEntries(personal, now = LocalDateTime.now())
                                    else -> state.overview
                                },
                        )
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
                            latestPersonal = null
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
                            latestPersonal = null
                            // Revoked/unauthorized accounts must not fall back to stale class data.
                            val status = (result.error as? AppError.Http)?.status
                            val local =
                                if (status == 401 || status == 403) {
                                    null
                                } else {
                                    when (val personal = personalRepository.list()) {
                                        is Outcome.Success -> personal.value.takeIf { it.isNotEmpty() }
                                        is Outcome.Failure -> null
                                    }
                                }
                            if (local != null) {
                                latestPersonal = local
                                _state.value =
                                    HomeUiState(
                                        loading = false,
                                        overview = TodaySchedule.calculateEntries(local, now = LocalDateTime.now()),
                                        fromCache = true,
                                        errorMessage = result.error.userMessage,
                                    )
                            } else {
                                _state.value =
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
