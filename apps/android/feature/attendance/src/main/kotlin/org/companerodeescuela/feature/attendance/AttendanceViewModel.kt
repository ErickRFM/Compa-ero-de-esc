package org.companerodeescuela.feature.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.companerodeescuela.core.attendance.AttendanceRepository
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.core.database.LocalAttendanceRecord
import org.companerodeescuela.shared.contracts.AttendanceQrInspectionRequest
import org.companerodeescuela.shared.contracts.AttendanceQrInspectionResponse
import org.companerodeescuela.shared.contracts.AttendanceQrInspectionStatus
import org.companerodeescuela.shared.contracts.AttendanceQrResponse
import org.companerodeescuela.shared.contracts.AttendanceRecordResponse
import org.companerodeescuela.shared.contracts.AttendanceRosterResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionResponse
import org.companerodeescuela.shared.contracts.AttendanceStatus
import org.companerodeescuela.shared.contracts.ClassOccurrenceContract
import org.companerodeescuela.shared.contracts.CreateAttendanceSessionRequest
import org.companerodeescuela.shared.contracts.ReviewAttendanceRequest
import org.companerodeescuela.shared.contracts.UserRole

enum class AttendanceMode {
    LOADING,
    STUDENT,
    TEACHER,
    UNSUPPORTED,
}

enum class QrVisualState {
    IDLE,
    LOADING,
    ACTIVE,
    RENEWING,
    UNAVAILABLE,
}

data class AttendanceUiState(
    val mode: AttendanceMode = AttendanceMode.LOADING,
    val loading: Boolean = true,
    val actionInProgress: Boolean = false,
    val userId: String? = null,
    val activeSessions: List<AttendanceSessionResponse> = emptyList(),
    val occurrences: List<ClassOccurrenceContract> = emptyList(),
    val teacherSession: AttendanceSessionResponse? = null,
    val qr: AttendanceQrResponse? = null,
    val qrVisualState: QrVisualState = QrVisualState.IDLE,
    val roster: AttendanceRosterResponse? = null,
    val localRecords: List<LocalAttendanceRecord> = emptyList(),
    val scannerOpen: Boolean = false,
    val scannerSessionHint: String? = null,
    val pendingQrToken: String? = null,
    val qrInspection: AttendanceQrInspectionResponse? = null,
    val successMessage: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class AttendanceViewModel @Inject constructor(
    private val repository: AttendanceRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AttendanceUiState())
    val state: StateFlow<AttendanceUiState> = _state.asStateFlow()

    private var localRecordsJob: Job? = null
    private var qrRotationJob: Job? = null
    private var rosterPollingJob: Job? = null

    init {
        bootstrap()
    }

    fun refresh() {
        when (_state.value.mode) {
            AttendanceMode.STUDENT -> refreshStudent()
            AttendanceMode.TEACHER -> refreshTeacher()
            AttendanceMode.LOADING,
            AttendanceMode.UNSUPPORTED,
            -> bootstrap()
        }
    }

    fun openScanner(sessionId: String? = null) {
        _state.update {
            it.copy(
                scannerOpen = true,
                scannerSessionHint = sessionId,
                qrInspection = null,
                pendingQrToken = null,
                successMessage = null,
                errorMessage = null,
            )
        }
    }

    fun openGenericScanner() {
        openScanner(null)
    }

    fun dismissScanner() {
        _state.update { it.copy(scannerOpen = false, scannerSessionHint = null) }
    }

    fun inspectQr(rawToken: String) {
        val token = rawToken.trim()
        val sessionId = _state.value.scannerSessionHint
            ?: AttendanceQrTokenParser.sessionId(token)
        if (sessionId.isNullOrBlank()) {
            _state.update {
                it.copy(
                    scannerOpen = false,
                    scannerSessionHint = null,
                    qrInspection = null,
                    pendingQrToken = null,
                    errorMessage = "No reconocimos un código QR de asistencia válido.",
                )
            }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = true, errorMessage = null) }
            when (
                val result = repository.inspectQr(
                    AttendanceQrInspectionRequest(
                        sessionId = sessionId,
                        token = token,
                    ),
                )
            ) {
                is Outcome.Success -> {
                    _state.update {
                        it.copy(
                            actionInProgress = false,
                            scannerOpen = false,
                            scannerSessionHint = null,
                            pendingQrToken = token,
                            qrInspection = result.value,
                            errorMessage = null,
                        )
                    }
                }
                is Outcome.Failure -> {
                    _state.update {
                        it.copy(
                            actionInProgress = false,
                            scannerOpen = false,
                            scannerSessionHint = null,
                            qrInspection = null,
                            pendingQrToken = null,
                            errorMessage = result.error.userMessage,
                        )
                    }
                }
            }
        }
    }

    fun submitInspectedQr() {
        val inspection = _state.value.qrInspection ?: return
        val token = _state.value.pendingQrToken ?: return
        val session = inspection.session ?: return
        if (inspection.status != AttendanceQrInspectionStatus.VALID) return

        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = true, errorMessage = null) }
            when (val result = repository.enqueueAttempt(session.id, token)) {
                is Outcome.Success -> {
                    _state.update {
                        it.copy(
                            actionInProgress = false,
                            qrInspection = null,
                            pendingQrToken = null,
                            successMessage =
                                "Pase guardado en este dispositivo. El servidor confirmará el resultado.",
                        )
                    }
                }
                is Outcome.Failure -> {
                    _state.update {
                        it.copy(
                            actionInProgress = false,
                            errorMessage = result.error.userMessage,
                        )
                    }
                }
            }
        }
    }

    fun clearQrInspection() {
        _state.update {
            it.copy(
                qrInspection = null,
                pendingQrToken = null,
            )
        }
    }

    fun openAttendance(occurrence: ClassOccurrenceContract) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    actionInProgress = true,
                    successMessage = null,
                    errorMessage = null,
                )
            }
            val request = CreateAttendanceSessionRequest(
                occurrenceId = occurrence.id,
                occurrenceDate = occurrence.date,
            )
            when (val result = repository.openSession(request)) {
                is Outcome.Success -> {
                    _state.update {
                        it.copy(
                            actionInProgress = false,
                            teacherSession = result.value,
                            successMessage = "Pase abierto. El QR se renueva automáticamente.",
                        )
                    }
                    startQrRotation(result.value.id)
                    startRosterPolling(result.value.id)
                }
                is Outcome.Failure -> {
                    _state.update {
                        it.copy(
                            actionInProgress = false,
                            errorMessage = result.error.userMessage,
                        )
                    }
                }
            }
        }
    }

    fun closeTeacherSession() {
        val sessionId = _state.value.teacherSession?.id ?: return
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = true, errorMessage = null) }
            when (val result = repository.closeSession(sessionId)) {
                is Outcome.Success -> {
                    qrRotationJob?.cancel()
                    rosterPollingJob?.cancel()
                    _state.update {
                        it.copy(
                            actionInProgress = false,
                            teacherSession = null,
                            qr = null,
                            qrVisualState = QrVisualState.IDLE,
                            roster = null,
                            successMessage = "Pase cerrado.",
                        )
                    }
                    refreshTeacher()
                }
                is Outcome.Failure -> {
                    _state.update {
                        it.copy(
                            actionInProgress = false,
                            errorMessage = result.error.userMessage,
                        )
                    }
                }
            }
        }
    }

    fun refreshRoster() {
        val sessionId = _state.value.teacherSession?.id ?: return
        viewModelScope.launch {
            when (val result = repository.roster(sessionId)) {
                is Outcome.Success -> _state.update { it.copy(roster = result.value) }
                is Outcome.Failure -> _state.update { it.copy(errorMessage = result.error.userMessage) }
            }
        }
    }

    fun reviewRecord(
        record: AttendanceRecordResponse,
        status: AttendanceStatus,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = true, errorMessage = null) }
            when (
                val result = repository.review(
                    recordId = record.id,
                    request = ReviewAttendanceRequest(status = status),
                )
            ) {
                is Outcome.Success -> {
                    _state.update {
                        val current = it.roster
                        it.copy(
                            actionInProgress = false,
                            roster = current?.copy(
                                records = current.records.map { item ->
                                    if (item.id == result.value.id) result.value else item
                                },
                            ),
                            successMessage = "Registro actualizado por el servidor.",
                        )
                    }
                }
                is Outcome.Failure -> _state.update {
                    it.copy(
                        actionInProgress = false,
                        errorMessage = result.error.userMessage,
                    )
                }
            }
        }
    }

    private fun bootstrap() {
        viewModelScope.launch {
            _state.update { it.copy(mode = AttendanceMode.LOADING, loading = true) }
            when (val claimsResult = repository.localSessionClaims()) {
                is Outcome.Success -> {
                    val claims = claimsResult.value
                    val mode = when {
                        UserRole.TEACHER in claims.roles -> AttendanceMode.TEACHER
                        UserRole.STUDENT in claims.roles -> AttendanceMode.STUDENT
                        else -> AttendanceMode.UNSUPPORTED
                    }
                    _state.update {
                        it.copy(
                            mode = mode,
                            userId = claims.userId,
                            loading = false,
                            errorMessage = null,
                        )
                    }
                    observeLocalRecords(claims.userId)
                    when (mode) {
                        AttendanceMode.STUDENT -> refreshStudent()
                        AttendanceMode.TEACHER -> refreshTeacher()
                        else -> Unit
                    }
                }
                is Outcome.Failure -> _state.update {
                    it.copy(
                        mode = AttendanceMode.UNSUPPORTED,
                        loading = false,
                        errorMessage = claimsResult.error.userMessage,
                    )
                }
            }
        }
    }

    private fun observeLocalRecords(ownerId: String) {
        localRecordsJob?.cancel()
        localRecordsJob = viewModelScope.launch {
            repository.observe(ownerId).collect { rows ->
                _state.update { it.copy(localRecords = rows) }
            }
        }
    }

    private fun refreshStudent() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, errorMessage = null) }
            val week = repository.academicWeek(LocalDate.now().toString())
            val sessions = repository.activeStudentSessions()

            val occurrences = week.valueOrNull()?.occurrences.orEmpty()
            when (sessions) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        loading = false,
                        activeSessions = sessions.value,
                        occurrences = occurrences,
                    )
                }
                is Outcome.Failure -> _state.update {
                    it.copy(
                        loading = false,
                        occurrences = occurrences,
                        errorMessage = sessions.error.userMessage,
                    )
                }
            }
        }
    }

    private fun refreshTeacher() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, errorMessage = null) }
            val week = repository.academicWeek(LocalDate.now().toString())
            val active = repository.activeTeacherSessions()

            val occurrences = week.valueOrNull()?.occurrences.orEmpty()
            val activeSessions = active.valueOrNull().orEmpty()
            val activeSession = activeSessions.firstOrNull()

            _state.update {
                it.copy(
                    loading = false,
                    occurrences = occurrences,
                    activeSessions = activeSessions,
                    teacherSession = activeSession,
                    errorMessage = when {
                        week is Outcome.Failure -> week.error.userMessage
                        active is Outcome.Failure -> active.error.userMessage
                        else -> null
                    },
                )
            }

            if (activeSession != null) {
                startQrRotation(activeSession.id)
                startRosterPolling(activeSession.id)
            }
        }
    }

    private fun startQrRotation(sessionId: String) {
        qrRotationJob?.cancel()
        qrRotationJob = viewModelScope.launch {
            while (_state.value.teacherSession?.id == sessionId) {
                _state.update {
                    it.copy(
                        qrVisualState = if (it.qr == null) {
                            QrVisualState.LOADING
                        } else {
                            QrVisualState.RENEWING
                        },
                    )
                }
                when (val result = repository.issueQr(sessionId)) {
                    is Outcome.Success -> {
                        _state.update {
                            it.copy(
                                qr = result.value,
                                qrVisualState = QrVisualState.ACTIVE,
                                errorMessage = null,
                            )
                        }
                        delay(result.value.rotateAfterSeconds.coerceAtLeast(5) * 1_000L)
                    }
                    is Outcome.Failure -> {
                        _state.update {
                            it.copy(
                                qr = null,
                                qrVisualState = QrVisualState.UNAVAILABLE,
                                errorMessage = result.error.userMessage,
                            )
                        }
                        delay(QR_RETRY_DELAY_MS)
                    }
                }
            }
        }
    }

    private fun startRosterPolling(sessionId: String) {
        rosterPollingJob?.cancel()
        rosterPollingJob = viewModelScope.launch {
            while (_state.value.teacherSession?.id == sessionId) {
                when (val result = repository.roster(sessionId)) {
                    is Outcome.Success -> _state.update { it.copy(roster = result.value) }
                    is Outcome.Failure -> Unit
                }
                delay(ROSTER_POLL_INTERVAL_MS)
            }
        }
    }

    override fun onCleared() {
        qrRotationJob?.cancel()
        rosterPollingJob?.cancel()
        localRecordsJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val QR_RETRY_DELAY_MS = 5_000L
        const val ROSTER_POLL_INTERVAL_MS = 4_000L
    }
}
