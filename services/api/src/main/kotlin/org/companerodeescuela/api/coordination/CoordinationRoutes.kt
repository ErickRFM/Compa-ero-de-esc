package org.companerodeescuela.api.coordination

import io.ktor.http.HttpHeaders
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import java.time.Instant
import org.companerodeescuela.api.academic.groups.AcademicGroupRecord
import org.companerodeescuela.api.academic.groups.AcademicGroupRepository
import org.companerodeescuela.api.auth.AuthTokenService
import org.companerodeescuela.api.auth.PlatformAccountRepository
import org.companerodeescuela.api.auth.requireNativeActor
import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.plugins.requestId
import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.CoordinationOverview
import org.companerodeescuela.shared.contracts.UserRole

internal fun countActiveGroupsInInstitution(
    groups: List<AcademicGroupRecord>,
    institutionId: String,
    at: Instant,
): Int = groups.count {
    it.active && it.scopeRevision >= 0L &&
        it.institutionId == institutionId &&
        it.startsAt?.let { start -> !start.isAfter(at) } == true &&
        it.endsAt?.isAfter(at) == true
}

fun Route.coordinationRoutes(
    settings: ApiSettings,
    groups: AcademicGroupRepository,
    accounts: PlatformAccountRepository,
) {
    route("/coordination") {
        if (!settings.hasAuthentication) {
            get("/overview") { throw ApiException.DependencyUnavailable("Authentication is not configured") }
            return@route
        }
        val auth = AuthTokenService(settings)
        authenticate(AuthTokenService.PROVIDER_NAME) {
            get("/overview") {
                val actor = call.requireNativeActor(auth)
                // Never rely on UI role intention or stale token claims for institutional scope.
                val live = accounts.findById(actor.id) ?: throw ApiException.Unauthorized()
                if (!live.permitsSession || live.accountStatus != AccountStatus.ACTIVE ||
                    UserRole.COORDINATOR !in live.roles) throw ApiException.Forbidden("Active coordinator role required")
                val institutionId = live.institutionId?.takeIf(String::isNotBlank)
                    ?: throw ApiException.Forbidden("Institution linkage required")
                val now = Instant.now()
                val total = countActiveGroupsInInstitution(groups.list(), institutionId, now)
                call.response.headers.append(HttpHeaders.CacheControl, "no-store")
                call.respond(ApiResponse(CoordinationOverview(total, now.epochSecond), call.requestId()))
            }
        }
    }
}
