package org.companerodeescuela.api.attendance

import org.companerodeescuela.api.academic.NativeAcademicProjection
import org.companerodeescuela.api.auth.PlatformAccountRepository
import org.companerodeescuela.api.channel.ChannelTaskService
import org.companerodeescuela.api.devices.PushOutboxItem
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.presence.SchoolPresenceService
import org.companerodeescuela.shared.contracts.AttendanceIdentitySource
import org.companerodeescuela.shared.contracts.AttendancePushContract
import org.companerodeescuela.shared.contracts.AttendanceSessionStatus
import org.companerodeescuela.shared.contracts.UserRole
import java.time.Clock

/** Revalidate class authority when delivering a queued notice, including retries. */
class AttendancePushEligibility(
    private val repository: AttendanceRepository,
    private val projection: NativeAcademicProjection,
    private val accounts: PlatformAccountRepository,
    private val presence: SchoolPresenceService?,
    private val clock: Clock = Clock.systemUTC(),
    private val institutions: org.companerodeescuela.api.institutions.InstitutionRepository? = null,
    private val tasks: ChannelTaskService? = null,
) {
    suspend fun allows(item: PushOutboxItem): Boolean {
        if (item.data["type"] == "channel_task") {
            val channelId = item.data["channelId"] ?: return false
            val taskId = item.data["taskId"] ?: return false
            return tasks?.eligibleTaskNotice(item.owner, channelId, taskId) ?: false
        }
        if (item.data["type"] == org.companerodeescuela.api.integrations.academic.siii.SiiiAcademicChangeNotifier.TYPE) {
            val id = item.owner.takeIf { it.startsWith("NATIVE:") }?.removePrefix("NATIVE:") ?: return false
            val current = accounts.findById(id) ?: return false
            val institution = item.data["institutionId"] ?: return false
            if (institutions?.findEnabled(institution) == null) return false
            return current.permitsSession &&
                current.emailVerifiedAt != null &&
                current.institutionId == institution &&
                current.roles.any { it == UserRole.STUDENT || it == UserRole.TEACHER }
        }
        if (item.data["type"] != AttendancePushContract.TYPE) return true
        val studentId = item.owner.takeIf { it.startsWith("NATIVE:") }?.removePrefix("NATIVE:") ?: return false
        val sessionId = item.data[AttendancePushContract.SESSION_ID] ?: return false
        val session = repository.findSession(sessionId) ?: return false
        if (session.identitySource != AttendanceIdentitySource.NATIVE ||
            session.status != AttendanceSessionStatus.OPEN ||
            session.closedAtEpochSeconds != null ||
            session.closesAtEpochSeconds <= clock.instant().epochSecond
        ) {
            return false
        }
        val student = accounts.findById(studentId) ?: return false
        if (!student.permitsSession ||
            student.emailVerifiedAt == null ||
            UserRole.STUDENT !in student.roles ||
            student.institutionId != session.institutionId
        ) {
            return false
        }
        val schoolPresence = presence ?: throw ApiException.DependencyUnavailable("Institutional entry verification is unavailable")
        val actor = student.toUserSummary()
        return try {
            projection.requireSession(actor, session)
            schoolPresence.activeFor(studentId, actor) != null
        } catch (_: ApiException.Forbidden) {
            false
        } catch (_: ApiException.Unauthorized) {
            false
        } catch (_: ApiException.NotFound) {
            false
        }
        // Authority outages propagate: the outbox retries without sending stale notices.
    }
}
