package org.companerodeescuela.core.attendance

import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import org.companerodeescuela.core.common.result.AppError
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.core.database.AttendanceLocalStore
import org.companerodeescuela.core.database.LocalAttendanceRecord
import org.companerodeescuela.core.security.PlatformSessionClaims
import org.companerodeescuela.core.security.SessionTokenInspector
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.shared.contracts.AcademicWeekResponse
import org.companerodeescuela.shared.contracts.AttendanceQrInspectionRequest
import org.companerodeescuela.shared.contracts.AttendanceQrInspectionResponse
import org.companerodeescuela.shared.contracts.AttendanceQrResponse
import org.companerodeescuela.shared.contracts.AttendanceRecordResponse
import org.companerodeescuela.shared.contracts.AttendanceRosterResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionResponse
import org.companerodeescuela.shared.contracts.CreateAttendanceSessionRequest
import org.companerodeescuela.shared.contracts.ClassCallConfirmationRequest
import org.companerodeescuela.shared.contracts.TeacherCampusRosterResponse
import org.companerodeescuela.shared.contracts.ReviewAttendanceRequest
import org.companerodeescuela.shared.contracts.SchoolNetworkEvidence
import org.companerodeescuela.shared.contracts.SchoolPresenceResponse
import org.companerodeescuela.shared.contracts.StartSchoolPresenceRequest

data class QrEvidenceCapture(
    val localRecord: LocalAttendanceRecord,
    val inspection: AttendanceQrInspectionResponse?,
)

class AttendanceRepository(
    private val localStore: AttendanceLocalStore,
    private val scheduler: AttendanceSyncEnqueuer,
    private val remoteClient: AttendanceRemoteClient,
    private val offlineQrStore: AttendanceQrPackStore = NoopAttendanceQrPackStore,
    private val sessionTokenStore: SessionTokenStore? = null,
    private val networkEvidenceProvider: SchoolNetworkEvidenceProvider =
        SchoolNetworkEvidenceProvider { null },
    private val clock: Clock = Clock.systemUTC(),
    private val newOperationId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun localSessionClaims(): Outcome<PlatformSessionClaims> {
        // Identity stored in the encrypted local session can still label an
        // offline *pending* capture. The API authenticates the sync later.
        val localClaims = sessionTokenStore?.readAccessToken()
            ?.let(SessionTokenInspector::inspect)
        if (localClaims != null) return Outcome.Success(localClaims)
        val token = currentToken().valueOrNull()
            ?: return Outcome.Failure(AppError.Http(status = 401))
        val claims = SessionTokenInspector.inspect(token)
            ?: return Outcome.Failure(AppError.Http(status = 401))
        return Outcome.Success(claims)
    }

    suspend fun schoolPresence(): Outcome<SchoolPresenceResponse?> =
        withToken(remoteClient::schoolPresence)

    suspend fun startSchoolPresence(qrToken: String): Outcome<SchoolPresenceResponse> {
        val network = networkEvidenceProvider.current()
            ?: return Outcome.Failure(
                AppError.Network("Connect to the school Wi-Fi before starting the school day"),
            )
        val request = StartSchoolPresenceRequest(
            operationId = newOperationId(),
            qrToken = qrToken.trim(),
            network = network,
            deviceTimestampEpochSeconds = clock.instant().epochSecond,
        )
        return withToken { token -> remoteClient.startSchoolPresence(token, request) }
    }

    suspend fun closeSchoolPresence(): Outcome<SchoolPresenceResponse> =
        withToken(remoteClient::closeSchoolPresence)

    fun currentSchoolNetwork(): SchoolNetworkEvidence? = networkEvidenceProvider.current()

    suspend fun activeStudentSessions(): Outcome<List<AttendanceSessionResponse>> =
        withToken(remoteClient::activeStudentSessions)

    suspend fun activeTeacherSessions(): Outcome<List<AttendanceSessionResponse>> =
        withToken(remoteClient::activeTeacherSessions)

    suspend fun academicWeek(weekOf: String): Outcome<AcademicWeekResponse> =
        withToken { token -> remoteClient.academicWeek(token, weekOf) }

    suspend fun openSession(
        request: CreateAttendanceSessionRequest,
    ): Outcome<AttendanceSessionResponse> =
        withToken { token -> remoteClient.openSession(token, request) }

    /** Online confirmation requires live Wi-Fi evidence; do not queue as an auto-present. */
    suspend fun confirmClassCall(sessionId: String): Outcome<AttendanceRecordResponse> {
        val network = networkEvidenceProvider.current()
            ?: return Outcome.Failure(AppError.Network("Conéctate al Wi-Fi escolar para confirmar tu asistencia."))
        val request = ClassCallConfirmationRequest(
            operationId = newOperationId(),
            schoolNetwork = network,
        )
        return withToken { token -> remoteClient.confirmClassCall(token, sessionId, request) }
    }

    suspend fun campusRoster(occurrenceId: String, date: String): Outcome<TeacherCampusRosterResponse> =
        withToken { token -> remoteClient.campusRoster(token, occurrenceId, date) }

    suspend fun myClassRecord(sessionId: String): Outcome<AttendanceRecordResponse?> =
        withToken { token -> remoteClient.myClassRecord(token, sessionId) }

    /**
     * Cache all remaining signed QR rotations while online. Safe to display
     * later without Wi-Fi, but student evidence stays pending until review.
     */
    suspend fun prepareOfflineQrPack(sessionId: String, ownerId: String): Outcome<Boolean> {
        val now = clock.instant().epochSecond
        if (offlineQrStore.read(ownerId, sessionId).any { it.expiresAtEpochSeconds > now }) {
            return Outcome.Success(true)
        }
        return when (val pack = withToken { token -> remoteClient.issueQrPack(token, sessionId) }) {
            is Outcome.Success -> {
                offlineQrStore.save(ownerId, sessionId, pack.value)
                Outcome.Success(pack.value.any { it.expiresAtEpochSeconds > now })
            }
            is Outcome.Failure -> pack
        }
    }

    fun rememberTeacherSession(ownerId: String, session: AttendanceSessionResponse) =
        offlineQrStore.rememberSession(ownerId, session)

    fun restoreTeacherSession(ownerId: String): AttendanceSessionResponse? =
        offlineQrStore.restoreSession(ownerId)?.takeIf {
            it.closedAtEpochSeconds == null && it.closesAtEpochSeconds > clock.instant().epochSecond
        }

    fun forgetTeacherSession(ownerId: String) = offlineQrStore.clearSession(ownerId)

    suspend fun issueQr(sessionId: String, ownerId: String = ""): Outcome<AttendanceQrResponse> {
        val now = clock.instant().epochSecond
        val cached = offlineQrStore.read(ownerId, sessionId)
            .lastOrNull { now >= it.issuedAtEpochSeconds && now < it.expiresAtEpochSeconds }
        return if (cached != null) Outcome.Success(cached)
        else withToken { token -> remoteClient.issueQr(token, sessionId) }
    }

    suspend fun inspectQr(
        request: AttendanceQrInspectionRequest,
    ): Outcome<AttendanceQrInspectionResponse> =
        withToken { token -> remoteClient.inspectQr(token, request) }

    /**
     * Persists QR evidence before attempting any network inspection.
     *
     * Network failure after persistence is intentionally represented as a successful
     * capture with a null inspection; WorkManager owns eventual delivery.
     */
    suspend fun captureQrEvidence(
        sessionId: String,
        qrToken: String,
    ): Outcome<QrEvidenceCapture> {
        val local = when (val captured = enqueueAttempt(sessionId, qrToken)) {
            is Outcome.Success -> captured.value
            is Outcome.Failure -> return captured
        }
        val inspection = when (
            val inspected = inspectQr(
                AttendanceQrInspectionRequest(
                    sessionId = sessionId,
                    token = qrToken,
                ),
            )
        ) {
            is Outcome.Success -> inspected.value
            is Outcome.Failure -> null
        }
        return Outcome.Success(
            QrEvidenceCapture(
                localRecord = local,
                inspection = inspection,
            ),
        )
    }

    suspend fun closeSession(sessionId: String): Outcome<AttendanceSessionResponse> =
        withToken { token -> remoteClient.closeSession(token, sessionId) }

    suspend fun roster(sessionId: String): Outcome<AttendanceRosterResponse> =
        withToken { token -> remoteClient.roster(token, sessionId) }

    suspend fun review(
        recordId: String,
        request: ReviewAttendanceRequest,
    ): Outcome<AttendanceRecordResponse> =
        withToken { token -> remoteClient.review(token, recordId, request) }

    suspend fun enqueueAttempt(
        sessionId: String,
        qrToken: String? = null,
    ): Outcome<LocalAttendanceRecord> {
        if (sessionId.isBlank()) {
            return Outcome.Failure(
                AppError.Serialization("Attendance session id was blank"),
            )
        }

        val normalizedQrToken = qrToken?.trim()?.takeIf(String::isNotEmpty)
        if (normalizedQrToken != null && normalizedQrToken.length > MAX_QR_TOKEN_LENGTH) {
            return Outcome.Failure(
                AppError.Serialization("Attendance QR token was too long"),
            )
        }

        // If the access token expired during an outage, local signed-QR
        // evidence can still be queued under the previously logged-in owner.
        // It is NOT a server-authorized attendance; sync requires fresh auth.
        val liveToken = currentToken().valueOrNull()
        val token = liveToken ?: if (normalizedQrToken != null &&
            networkEvidenceProvider.current() == null
        ) {
            sessionTokenStore?.readAccessToken()
        } else null
        val claims = token?.let(SessionTokenInspector::inspect)
            ?: return Outcome.Failure(AppError.Http(status = 401))

        return try {
            val local = localStore.enqueue(
                operationId = newOperationId(),
                ownerId = claims.userId,
                sessionId = sessionId,
                deviceTimestampEpochSeconds = clock.instant().epochSecond,
                qrToken = normalizedQrToken,
                schoolNetwork = networkEvidenceProvider.current(),
            )
            scheduler.schedule()
            Outcome.Success(local)
        } catch (error: Exception) {
            Outcome.Failure(
                AppError.Unknown(
                    technicalDetail = "Could not queue attendance: " + error::class.simpleName,
                ),
            )
        }
    }

    suspend fun resumePending() {
        if (currentToken() is Outcome.Success) {
            scheduler.schedule()
        }
    }

    fun observe(ownerId: String): Flow<List<LocalAttendanceRecord>> =
        localStore.observe(ownerId)

    private suspend fun currentToken(): Outcome<String> {
        return remoteClient.currentAccessToken()
    }

    private suspend fun <T> withToken(
        block: suspend (String) -> Outcome<T>,
    ): Outcome<T> {
        return when (val token = currentToken()) {
            is Outcome.Success -> block(token.value)
            is Outcome.Failure -> token
        }
    }

    private companion object {
        const val MAX_QR_TOKEN_LENGTH = 2_048
    }
}
