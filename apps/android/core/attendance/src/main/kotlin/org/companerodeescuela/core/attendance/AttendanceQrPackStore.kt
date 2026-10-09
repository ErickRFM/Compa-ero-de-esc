package org.companerodeescuela.core.attendance

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import kotlin.math.abs
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
class AndroidAttendanceQrPackStore(
    context: Context,
    private val wallTimeSeconds: () -> Long,
    private val elapsedRealtimeMillis: () -> Long,
    private val bootCount: () -> Int,
) : AttendanceQrPackStore {
    @Inject constructor(@ApplicationContext context: Context) : this(
        context,
        { System.currentTimeMillis() / 1000 },
        SystemClock::elapsedRealtime,
        { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1) },
    )
    private val preferences = context.getSharedPreferences("signed_teacher_attendance_qrs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    override fun read(ownerId: String, sessionId: String): List<AttendanceQrResponse> {
        if (ownerId.isBlank() || sessionId.isBlank()) return emptyList()
        val data = preferences.getString(packKey(ownerId, sessionId), null) ?: return emptyList()
        val pack = runCatching { json.decodeFromString<SavedQrPack>(data) }.getOrNull()
            ?: return emptyList()
        val elapsed = elapsedRealtimeMillis() - pack.savedAtElapsedMillis
        val wallDelta = wallTimeSeconds() - pack.savedAtWallSeconds
        if (pack.bootCount < 0 || bootCount() != pack.bootCount || elapsed < 0 ||
            abs(wallDelta - elapsed / 1000) > 5
        ) {
            clearSession(ownerId)
            return emptyList()
        }
        return pack.slots.filter { it.expiresAtEpochSeconds > wallTimeSeconds() }
    }

    override fun save(ownerId: String, sessionId: String, slots: List<AttendanceQrResponse>) {
        if (ownerId.isBlank() || sessionId.isBlank()) return
        val pack = SavedQrPack(slots, wallTimeSeconds(), elapsedRealtimeMillis(), bootCount())
        preferences.edit().putString(packKey(ownerId, sessionId), json.encodeToString(pack)).apply()
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
        preferences.edit().also { editor ->
            preferences.all.keys.filter { it.startsWith("pack:$ownerId:") || it.startsWith("session:$ownerId:") }.forEach(editor::remove)
            editor.remove(sessionKey(ownerId))
        }.apply()
    }

    private fun sessionKey(ownerId: String) = "session:$ownerId"
    private fun packKey(ownerId: String, sessionId: String) = "pack:$ownerId:$sessionId"
}

@Serializable
private data class SavedQrPack(
    val slots: List<AttendanceQrResponse>,
    val savedAtWallSeconds: Long,
    val savedAtElapsedMillis: Long,
    val bootCount: Int,
)
