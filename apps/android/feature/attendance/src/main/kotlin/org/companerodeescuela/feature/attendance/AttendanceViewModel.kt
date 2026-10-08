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
import org.companerodeescuela.shared.contracts.AttendanceDisposition
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
import org.companerodeescuela.shared.contracts.SchoolPresenceResponse
import org.companerodeescuela.shared.contracts.UserRole

enum class AttendanceMode {
    LOADING,
    STUDENT,
    TEACHER,
    UNSUPPORTED,
}

enum class ScannerPurpose {
    ATTENDANCE,
    SCHOOL_DAY,
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
    val schoolPresence: SchoolPresenceResponse? = null,
    val schoolPresenceReceivedRealtime: Long? = null,
    val schoolNetworkSsid: String? = null,
    val scannerOpen: Boolean = false,
    val scannerPurpose: ScannerPurpose = ScannerPurpose.ATTENDANCE,
    val scannerSessionHint: String? = null,
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

    fun selectMode(requestedMode: AttendanceMode) {
        if (requestedMode !in setOf(AttendanceMode.STUDENT, AttendanceMode.TEACHER)) return

        viewModelScope.launch {
            when (val claimsResult = repository.localSessionClaims()) {
                is Outcome.Success -> {
                    val claims = claimsResult.value
                    val allowed = when (requestedMode) {
                        AttendanceMode.STUDENT -> UserRole.STUDENT in claims.roles
                        AttendanceMode.TEACHER -> UserRole.TEACHER in claims.roles
                        else -> false
                    }
                    if (!allowed) {
                        _state.update {
                            it.copy(
                                mode = AttendanceMode.UNSUPPORTED,
                                loading = false,
                                errorMessage = "Tu cuenta no tiene acceso a este modo de asistencia.",
                            )
                        }
                        return@launch
                    }

                    _state.update {
                        it.copy(
                            mode = requestedMode,
                            userId = claims.userId,
                            loading = false,
                            errorMessage = null,
                        )
                    }
                    observeLocalRecords(claims.userId)
                    when (requestedMode) {
                        AttendanceMode.STUDENT -> refreshStudent()
                        AttendanceMode.TEACHER -> refreshTeacher()
                        else -> Unit
                    }
                }
                is Outcome.Failure -> {
                    _state.update {
                        it.copy(
                            mode = AttendanceMode.UNSUPPORTED,
                            loading = false,
                            errorMessage = claimsResult.error.userMessage,
                        )
                    }
                }
            }
        }
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
                scannerPurpose = ScannerPurpose.ATTENDANCE,
                scannerSessionHint = sessionId,
                qrInspection = null,
                successMessage = null,
                errorMessage = null,
            )
        }
    }

    fun openGenericScanner() {
        openScanner(null)
    }

    fun openSchoolDayScanner() {
        val network = repository.currentSchoolNetwork()
        if (network == null) {
            _state.update {
                it.copy(
                    schoolNetworkSsid = null,
                    errorMessage = "Conéctate al Wi-Fi de la escuela antes de iniciar tu jornada.",
                )
            }
            return
        }
        _state.update {
            it.copy(
                scannerOpen = true,
                scannerPurpose = ScannerPurpose.SCHOOL_DAY,
                scannerSessionHint = null,
                schoolNetworkSsid = network.ssid,
                qrInspection = null,
                successMessage = null,
                errorMessage = null,
            )
        }
    }

    fun dismissScanner() {
        _state.update { it.copy(scannerOpen = false, scannerSessionHint = null) }
    }

    fun inspectQr(rawToken: String) {
        val token = rawToken.trim()
        if (_state.value.scannerPurpose == ScannerPurpose.SCHOOL_DAY) {
            startSchoolDay(token)
            return
        }
        val sessionId = _state.value.scannerSessionHint
            ?: AttendanceQrTokenParser.sessionId(token)
        if (sessionId.isNullOrBlank()) {
            _state.update {
                it.copy(
                    scannerOpen = false,
                    scannerSessionHint = null,
                    qrInspection = null,
                    errorMessage = "No reconocimos un código QR de asistencia válido.",
                )
            }
            return
        }

        viewModelScope.launch {
            _state.update {
                it.copy(
                    actionInProgress = true,
                    errorMessage = null,
                    successMessage = null,
                )
            }

            when (val captured = repository.captureQrEvidence(sessionId, token)) {
                is Outcome.Success -> {
                    val inspection = captured.value.inspection
                    _state.update {
                        it.copy(
                            actionInProgress = false,
                            scannerOpen = false,
                            scannerSessionHint = null,
                            qrInspection = inspection,
                            errorMessage = null,
                            successMessage = if (inspection == null) {
                                "QR guardado. No pudimos comprobarlo ahora; se verificará automáticamente cuando haya conexión."
                            } else {
                                "QR guardado en este dispositivo. El servidor confirmará el resultado final."
                            },
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
                            errorMessage = captured.error.userMessage,
                        )
                    }
                }
            }
        }
    }

    private fun startSchoolDay(rawToken: String) {
        val token = rawToken.trim()
        if (token.isBlank()) {
            _state.update {
                it.copy(
                    scannerOpen = false,
                    errorMessage = "No reconocimos el QR institucional.",
                )
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = true, errorMessage = null) }
            when (val result = repository.startSchoolPresence(token)) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        actionInProgress = false,
                        scannerOpen = false,
                        schoolPresence = result.value,
                        schoolPresenceReceivedRealtime = android.os.SystemClock.elapsedRealtime(),
                        schoolNetworkSsid = repository.currentSchoolNetwork()?.ssid,
                        successMessage = "Jornada escolar iniciada. Tu red y el QR institucional fueron verificados.",
                    )
                }
                is Outcome.Failure -> _state.update {
                    it.copy(
                        actionInProgress = false,
                        scannerOpen = false,
                        errorMessage = result.error.userMessage,
                    )
                }
            }
        }
    }

    fun clearQrInspection() {
        _state.update {
            it.copy(
                qrInspection = null,
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

    fun markRecord(
        record: AttendanceRecordResponse,
        disposition: AttendanceDisposition,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = true, errorMessage = null) }
            when (
                val result = repository.review(
                    recordId = record.id,
                    request = ReviewAttendanceRequest(
                        status = record.status,
                        disposition = disposition,
                    ),
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
                            successMessage = when (disposition) {
                                AttendanceDisposition.PRESENT -> "Alumno marcado como presente."
                                AttendanceDisposition.LATE -> "Alumno marcado con retardo."
                                AttendanceDisposition.ABSENT -> "Alumno marcado como ausente."
                            },
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
                        UserRole.STUDENT in claims.roles -> AttendanceMode.STUDENT
                        UserRole.TEACHER in claims.roles -> AttendanceMode.TEACHER
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
            val presence = repository.schoolPresence()
            val network = repository.currentSchoolNetwork()

            val occurrences = week.valueOrNull()?.occurrences.orEmpty()
            when (sessions) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        loading = false,
                        activeSessions = sessions.value,
                        occurrences = occurrences,
                        schoolPresence = presence.valueOrNull(),
                        schoolPresenceReceivedRealtime = android.os.SystemClock.elapsedRealtime(),
                        schoolNetworkSsid = network?.ssid,
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
