package org.companerodeescuela.feature.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
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
import org.companerodeescuela.shared.contracts.TeacherCampusRosterResponse
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
import org.companerodeescuela.shared.contracts.TeacherClassContext

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
    val occurrencesLoaded: Boolean = false,
    val sessionsLoaded: Boolean = false,
    val teacherSession: AttendanceSessionResponse? = null,
    val qr: AttendanceQrResponse? = null,
    val qrVisualState: QrVisualState = QrVisualState.IDLE,
    val roster: AttendanceRosterResponse? = null,
    val campusRoster: TeacherCampusRosterResponse? = null,
    val campusRosterError: String? = null,
    val confirmedClassCalls: Map<String, AttendanceRecordResponse> = emptyMap(),
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
    private var studentPollingJob: Job? = null
    private var requestedClassroom: TeacherClassContext? = null
    private var modeGeneration = 0L
    private var teacherRequests = SupervisorJob(viewModelScope.coroutineContext[Job])

    private fun launchTeacherRequest(block: suspend CoroutineScope.() -> Unit): Job =
        CoroutineScope(viewModelScope.coroutineContext + teacherRequests).launch(block = block)

    fun setClassroomContext(selection: TeacherClassContext?) {
        modeGeneration++
        studentPollingJob?.cancel()
        teacherRequests.cancel()
        teacherRequests = SupervisorJob(viewModelScope.coroutineContext[Job])
        requestedClassroom = selection
        qrRotationJob?.cancel()
        rosterPollingJob?.cancel()
        _state.update { it.copy(teacherSession = null, roster = null, campusRoster = null, qr = null,
            activeSessions = emptyList(), occurrences = emptyList(), occurrencesLoaded = false,
            sessionsLoaded = false, actionInProgress = false, campusRosterError = null,
            qrVisualState = QrVisualState.IDLE, successMessage = null, errorMessage = null) }
    }

    init {
        bootstrap()
    }

    fun selectMode(requestedMode: AttendanceMode) {
        if (requestedMode !in setOf(AttendanceMode.STUDENT, AttendanceMode.TEACHER)) return
        val generation = ++modeGeneration
        studentPollingJob?.cancel()

        viewModelScope.launch {
            val claimsResult = repository.localSessionClaims()
            if (generation != modeGeneration) return@launch
            when (claimsResult) {
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
                        AttendanceMode.STUDENT -> {
                            refreshStudent()
                            startStudentPolling()
                        }
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
                        successMessage = "Ingreso registrado. El QR fue reconocido; la presencia física aún no está verificada.",
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
        if (_state.value.actionInProgress || _state.value.mode != AttendanceMode.TEACHER ||
            _state.value.occurrences.none { it.id == occurrence.id }) return
        launchTeacherRequest {
            val availability = teacherPassOpenStatus(occurrence, LocalDateTime.now())
            if (availability != TeacherPassOpenStatus.AVAILABLE) {
                _state.update {
                    it.copy(
                        actionInProgress = false,
                        errorMessage = teacherPassUnavailableMessage(availability),
                    )
                }
                return@launchTeacherRequest
            }
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
            val result = repository.openSession(request)
            ensureActive()
            when (result) {
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
                    refreshCampusRoster(occurrence.id, occurrence.date)
                }
                is Outcome.Failure -> {
                    _state.update {
                        it.copy(
                            actionInProgress = false,
                            errorMessage = teacherPassOpenError(result.error),
                        )
                    }
                }
            }
        }
    }

    fun confirmClassCall(sessionId: String) {
        if (_state.value.actionInProgress || _state.value.confirmedClassCalls.containsKey(sessionId)) return
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = true, errorMessage = null, successMessage = null) }
            when (val result = repository.confirmClassCall(sessionId)) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        actionInProgress = false,
                        confirmedClassCalls = it.confirmedClassCalls + (sessionId to result.value),
                        successMessage = classCallRecordMessage(result.value),
                    )
                }
                is Outcome.Failure -> _state.update {
                    it.copy(actionInProgress = false, errorMessage = result.error.userMessage)
                }
            }
        }
    }

    private fun refreshCampusRoster(occurrenceId: String, date: String) {
        launchTeacherRequest {
            val result = repository.campusRoster(occurrenceId, date)
            ensureActive()
            when (result) {
                is Outcome.Success -> _state.update {
                    it.copy(campusRoster = result.value, campusRosterError = null)
                }
                is Outcome.Failure -> _state.update {
                    it.copy(campusRoster = null, campusRosterError = result.error.userMessage)
                }
            }
        }
    }

    fun closeTeacherSession() {
        val sessionId = _state.value.teacherSession?.id ?: return
        launchTeacherRequest {
            _state.update { it.copy(actionInProgress = true, errorMessage = null) }
            val result = repository.closeSession(sessionId)
            ensureActive()
            when (result) {
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
        launchTeacherRequest {
            val result = repository.roster(sessionId)
            ensureActive()
            when (result) {
                is Outcome.Success -> _state.update { it.copy(roster = result.value) }
                is Outcome.Failure -> _state.update { it.copy(errorMessage = result.error.userMessage) }
            }
        }
    }

    fun markRecord(
        record: AttendanceRecordResponse,
        disposition: AttendanceDisposition,
        note: String,
    ) {
        if (_state.value.actionInProgress || note.trim().length !in 3..500 ||
            _state.value.roster?.records?.none { it.id == record.id } != false) return
        launchTeacherRequest {
            _state.update { it.copy(actionInProgress = true, errorMessage = null) }
            val result = repository.review(
                    recordId = record.id,
                    request = ReviewAttendanceRequest(
                        status = record.status,
                        disposition = disposition,
                        note = note,
                    ),
                )
            ensureActive()
            when (result) {
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
        note: String,
    ) {
        if (_state.value.actionInProgress || note.trim().length !in 3..500 ||
            _state.value.roster?.records?.none { it.id == record.id } != false) return
        launchTeacherRequest {
            _state.update { it.copy(actionInProgress = true, errorMessage = null) }
            val result = repository.review(
                    recordId = record.id,
                    request = ReviewAttendanceRequest(status = status, note = note),
                )
            ensureActive()
            when (result) {
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
        val generation = modeGeneration
        viewModelScope.launch {
            if (generation != modeGeneration) return@launch
            _state.update { it.copy(mode = AttendanceMode.LOADING, loading = true) }
            val claimsResult = repository.localSessionClaims()
            if (generation != modeGeneration) return@launch
            when (claimsResult) {
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
                        AttendanceMode.STUDENT -> {
                            refreshStudent()
                            startStudentPolling()
                        }
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
        val generation = modeGeneration
        viewModelScope.launch {
            if (_state.value.mode != AttendanceMode.STUDENT || generation != modeGeneration) return@launch
            _state.update { it.copy(loading = true, errorMessage = null) }
            val week = repository.academicWeek(LocalDate.now().toString())
            if (_state.value.mode != AttendanceMode.STUDENT || generation != modeGeneration) return@launch
            val sessions = repository.activeStudentSessions()
            val presence = repository.schoolPresence()
            if (_state.value.mode != AttendanceMode.STUDENT || generation != modeGeneration) return@launch
            val presenceReceivedRealtime = android.os.SystemClock.elapsedRealtime()
            val network = repository.currentSchoolNetwork()

            val occurrences = week.valueOrNull()?.occurrences.orEmpty()
            val previousResults = if (sessions is Outcome.Success) {
                sessions.value.mapNotNull { session ->
                    (repository.myClassRecord(session.id) as? Outcome.Success)?.value
                        ?.let { record -> session.id to record }
                }.toMap()
            } else emptyMap()
            if (_state.value.mode != AttendanceMode.STUDENT || generation != modeGeneration) return@launch
            when (sessions) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        loading = false,
                        activeSessions = sessions.value,
                        confirmedClassCalls = previousResults,
                        occurrences = occurrences,
                        schoolPresence = presence.valueOrNull(),
                        schoolPresenceReceivedRealtime = presenceReceivedRealtime,
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
        launchTeacherRequest {
            _state.update { it.copy(loading = true, errorMessage = null) }
            val week = repository.academicWeek(LocalDate.now().toString())
            val active = repository.activeTeacherSessions()
            ensureActive()

            val context = requestedClassroom
            val matching = week.valueOrNull()?.occurrences.orEmpty().filter {
                context == null || context.matches(it.subjectName, it.groupName)
            }
            val ambiguous = context != null && matching.map { it.courseId }.distinct().size > 1
            val occurrences = if (ambiguous) emptyList() else matching
            val activeSessions = active.valueOrNull().orEmpty().filter {
                it.closedAtEpochSeconds == null && it.closesAtEpochSeconds > System.currentTimeMillis() / 1000 &&
                    (context == null || occurrences.any { occurrence -> occurrence.id == it.occurrenceId })
            }
            val activeSession = activeSessions.firstOrNull()

            _state.update {
                it.copy(
                    loading = false,
                    occurrences = occurrences,
                    occurrencesLoaded = week is Outcome.Success,
                    sessionsLoaded = active is Outcome.Success,
                    activeSessions = activeSessions,
                    teacherSession = activeSession,
                    errorMessage = when {
                        week is Outcome.Failure -> week.error.userMessage
                        active is Outcome.Failure -> active.error.userMessage
                        ambiguous -> "No hay una vinculación inequívoca entre esta clase y el horario institucional."
                        context != null && occurrences.isEmpty() -> "No hay horario autorizado para ${context.subjectName} · ${context.groupName.orEmpty()}."
                        else -> null
                    },
                )
            }

            if (activeSession != null) {
                startQrRotation(activeSession.id)
                startRosterPolling(activeSession.id)
            } else {
                qrRotationJob?.cancel()
                rosterPollingJob?.cancel()
                _state.update { it.copy(qr = null, roster = null, qrVisualState = QrVisualState.IDLE) }
            }
            val campusOccurrence = activeSession?.let { session ->
                occurrences.firstOrNull { it.id == session.occurrenceId }
            } ?: occurrences.filter { it.date == LocalDate.now().toString() }
                .minByOrNull { it.startsAt }
            if (campusOccurrence != null) {
                refreshCampusRoster(campusOccurrence.id, campusOccurrence.date)
            } else {
                _state.update { it.copy(campusRoster = null, campusRosterError = null) }
            }
        }
    }

    private fun startStudentPolling() {
        studentPollingJob?.cancel()
        studentPollingJob = viewModelScope.launch {
            while (_state.value.mode == AttendanceMode.STUDENT) {
                delay(STUDENT_POLL_INTERVAL_MS)
                if (_state.value.actionInProgress || _state.value.scannerOpen) continue
                when (val result = repository.activeStudentSessions()) {
                    is Outcome.Success -> {
                        val sessions = result.value
                        val priorIds = _state.value.activeSessions.map { it.id }.toSet()
                        val newSessions = sessions.filterNot { it.id in priorIds }
                        _state.update {
                            it.copy(
                                activeSessions = sessions,
                                successMessage = if (newSessions.isNotEmpty()) {
                                    "Nuevo pase de lista disponible. Confirma desde tu clase."
                                } else it.successMessage,
                            )
                        }
                    }
                    is Outcome.Failure -> Unit // Keep last known data on transient failures.
                }
            }
        }
    }

    private fun startQrRotation(sessionId: String) {
        qrRotationJob?.cancel()
        qrRotationJob = launchTeacherRequest {
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
                val result = repository.issueQr(sessionId)
                ensureActive()
                when (result) {
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
        rosterPollingJob = launchTeacherRequest {
            while (_state.value.teacherSession?.id == sessionId) {
                if ((_state.value.teacherSession?.closesAtEpochSeconds ?: 0) <= System.currentTimeMillis() / 1000) {
                    _state.update { it.copy(teacherSession = null, qr = null, roster = null, qrVisualState = QrVisualState.IDLE) }
                    refreshTeacher()
                    return@launchTeacherRequest
                }
                val result = repository.roster(sessionId)
                ensureActive()
                when (result) {
                    is Outcome.Success -> _state.update { it.copy(roster = result.value) }
                    is Outcome.Failure -> Unit
                }
                val occurrence = _state.value.occurrences.firstOrNull { it.id == _state.value.teacherSession?.occurrenceId }
                if (occurrence != null) {
                    val campus = repository.campusRoster(occurrence.id, occurrence.date)
                    ensureActive()
                    when (campus) {
                        is Outcome.Success -> _state.update {
                            it.copy(campusRoster = campus.value, campusRosterError = null)
                        }
                        is Outcome.Failure -> Unit // Do not wipe a valid roster on network drops.
                    }
                }
                delay(ROSTER_POLL_INTERVAL_MS)
            }
        }
    }

    override fun onCleared() {
        teacherRequests.cancel()
        qrRotationJob?.cancel()
        rosterPollingJob?.cancel()
        studentPollingJob?.cancel()
        localRecordsJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val QR_RETRY_DELAY_MS = 5_000L
        const val ROSTER_POLL_INTERVAL_MS = 4_000L
        const val STUDENT_POLL_INTERVAL_MS = 20_000L
    }
}
