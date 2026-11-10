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
import org.companerodeescuela.shared.contracts.MaticaProgressSummary
import org.companerodeescuela.shared.contracts.ScheduleEntry
import java.time.LocalDateTime
import javax.inject.Inject

data class HomeUiState(
    val loading: Boolean = true,
    val overview: TodayOverview? = null,
    val fromCache: Boolean = false,
    val updatedAtEpochSeconds: Long? = null,
    val errorMessage: String? = null,
    val institutionalAccessDenied: Boolean = false,
    val personalOnly: Boolean = false,
    val maticaProgress: MaticaProgressSummary? = null,
    val maticaLinked: Boolean? = null,
    val maticaLoading: Boolean = false,
    val maticaBusy: Boolean = false,
    val maticaError: String? = null,
)

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val repository: AcademicRepository,
        private val personalRepository: PersonalScheduleRepository,
        private val maticaRepository: MaticaProgressRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(HomeUiState())
        val state: StateFlow<HomeUiState> = _state.asStateFlow()
        private var latestAcademic: AcademicLoadResponse? = null
        private var latestPersonal: List<ScheduleEntry>? = null
        private var refreshJob: Job? = null
        private var maticaRefreshJob: Job? = null

        init {
            refresh()
            refreshMaticaProgress()
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
                                    personal != null ->
                                        TodaySchedule.calculateEntries(
                                            personal,
                                            state.overview?.studentName.orEmpty(),
                                            LocalDateTime.now(),
                                        )
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
                            val academic = result.value.academic
                            val personal =
                                if (academic.schedule.entries.none { it.source.name == "INSTITUTIONAL" }) {
                                    when (val saved = personalRepository.list()) {
                                        is Outcome.Success -> saved.value
                                        is Outcome.Failure -> emptyList()
                                    }
                                } else {
                                    emptyList()
                                }
                            val selected = TodaySchedule.select(academic, personal, LocalDateTime.now())
                            latestAcademic = academic.takeUnless { selected.usesPersonalSchedule }
                            latestPersonal = (academic.schedule.entries + personal).distinct().takeIf { selected.usesPersonalSchedule }
                            _state.update {
                                it.copy(
                                    loading = false,
                                    overview = selected.overview,
                                    fromCache = result.value.fromCache || selected.usesPersonalSchedule,
                                    updatedAtEpochSeconds = result.value.updatedAtEpochSeconds,
                                    errorMessage = null,
                                    institutionalAccessDenied = false,
                                    personalOnly = selected.usesPersonalSchedule,
                                )
                            }
                        }
                        is Outcome.Failure -> {
                            latestAcademic = null
                            latestPersonal = null
                            // Revoked/unauthorized accounts must not fall back to stale class data.
                            val status = (result.error as? AppError.Http)?.status
                            val local =
                                if (status == 401) {
                                    null
                                } else {
                                    when (val personal = personalRepository.list()) {
                                        is Outcome.Success -> personal.value.takeIf { it.isNotEmpty() }
                                        is Outcome.Failure -> null
                                    }
                                }
                            if (local != null) {
                                latestPersonal = local
                                _state.update {
                                    it.copy(
                                        loading = false,
                                        overview = TodaySchedule.calculateEntries(local, now = LocalDateTime.now()),
                                        fromCache = true,
                                        errorMessage = result.error.userMessage,
                                        institutionalAccessDenied = status == 403,
                                        personalOnly = true,
                                    )
                                }
                            } else {
                                _state.update {
                                    it.copy(
                                        loading = false,
                                        overview = null,
                                        fromCache = false,
                                        errorMessage = result.error.userMessage,
                                        institutionalAccessDenied = status == 403,
                                        personalOnly = false,
                                    )
                                }
                            }
                        }
                    }
                }
        }

        fun refreshMaticaProgress() {
            if (maticaRefreshJob?.isActive == true) return
            maticaRefreshJob =
                viewModelScope.launch {
                    _state.update { it.copy(maticaLoading = true, maticaError = null) }
                    when (val result = maticaRepository.load()) {
                        is Outcome.Success ->
                            _state.update {
                                it.copy(
                                    maticaLoading = false,
                                    maticaProgress = result.value,
                                    maticaLinked = result.value != null,
                                    maticaError = null,
                                )
                            }
                        is Outcome.Failure ->
                            _state.update {
                                it.copy(maticaLoading = false, maticaLinked = null, maticaError = result.error.userMessage)
                            }
                    }
                }
        }

        fun linkMatica(code: String) {
            if (_state.value.maticaBusy) return
            viewModelScope.launch {
                _state.update { it.copy(maticaBusy = true, maticaError = null) }
                when (val result = maticaRepository.link(code)) {
                    is Outcome.Success -> {
                        _state.update { it.copy(maticaBusy = false, maticaLinked = true, maticaError = null) }
                        refreshMaticaProgress()
                    }
                    is Outcome.Failure -> _state.update { it.copy(maticaBusy = false, maticaError = result.error.userMessage) }
                }
            }
        }

        fun unlinkMatica() {
            if (_state.value.maticaBusy) return
            viewModelScope.launch {
                _state.update { it.copy(maticaBusy = true, maticaError = null) }
                when (val result = maticaRepository.unlink()) {
                    is Outcome.Success ->
                        _state.update {
                            it.copy(
                                maticaBusy = false,
                                maticaLinked = false,
                                maticaProgress = null,
                                maticaError = null,
                            )
                        }
                    is Outcome.Failure -> _state.update { it.copy(maticaBusy = false, maticaError = result.error.userMessage) }
                }
            }
        }
    }
