package org.companerodeescuela.core.attendance

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.companerodeescuela.shared.contracts.AttendanceQrResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionResponse

/**
 * Device-private cache of server-signed QR slots. No signing key exists on Android.
 * Keys include the authenticated teacher ID to prevent account crossover.
 */
interface AttendanceQrPackStore {
    fun read(ownerId: String, sessionId: String): List<AttendanceQrResponse>
    fun save(ownerId: String, sessionId: String, slots: List<AttendanceQrResponse>)
    fun rememberSession(ownerId: String, session: AttendanceSessionResponse)
    fun restoreSession(ownerId: String): AttendanceSessionResponse?
    fun clearSession(ownerId: String)
}

object NoopAttendanceQrPackStore : AttendanceQrPackStore {
    override fun read(ownerId: String, sessionId: String) = emptyList<AttendanceQrResponse>()
    override fun save(ownerId: String, sessionId: String, slots: List<AttendanceQrResponse>) = Unit
    override fun rememberSession(ownerId: String, session: AttendanceSessionResponse) = Unit
    override fun restoreSession(ownerId: String): AttendanceSessionResponse? = null
    override fun clearSession(ownerId: String) = Unit
}

@Singleton
class AndroidAttendanceQrPackStore @Inject constructor(
    @ApplicationContext context: Context,
) : AttendanceQrPackStore {
    private val preferences = context.getSharedPreferences("signed_teacher_attendance_qrs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    override fun read(ownerId: String, sessionId: String): List<AttendanceQrResponse> {
        if (ownerId.isBlank() || sessionId.isBlank()) return emptyList()
        val data = preferences.getString(packKey(ownerId, sessionId), null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<AttendanceQrResponse>>(data) }
            .getOrDefault(emptyList())
    }

    override fun save(ownerId: String, sessionId: String, slots: List<AttendanceQrResponse>) {
        if (ownerId.isBlank() || sessionId.isBlank()) return
        preferences.edit().putString(packKey(ownerId, sessionId), json.encodeToString(slots)).apply()
    }

    override fun rememberSession(ownerId: String, session: AttendanceSessionResponse) {
        if (ownerId.isBlank()) return
        preferences.edit()
            .putString(sessionKey(ownerId), json.encodeToString(session))
            .apply()
    }

    override fun restoreSession(ownerId: String): AttendanceSessionResponse? {
        if (ownerId.isBlank()) return null
        val data = preferences.getString(sessionKey(ownerId), null) ?: return null
        return runCatching { json.decodeFromString<AttendanceSessionResponse>(data) }.getOrNull()
    }

    override fun clearSession(ownerId: String) {
        if (ownerId.isBlank()) return
        val session = restoreSession(ownerId)
        preferences.edit().also { editor ->
            session?.let { editor.remove(packKey(ownerId, it.id)) }
            editor.remove(sessionKey(ownerId))
        }.apply()
    }

    private fun sessionKey(ownerId: String) = "session:$ownerId"
    private fun packKey(ownerId: String, sessionId: String) = "pack:$ownerId:$sessionId"
}
