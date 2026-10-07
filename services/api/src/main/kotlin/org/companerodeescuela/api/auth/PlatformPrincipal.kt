package org.companerodeescuela.api.auth

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.UserRole

fun ApplicationCall.requirePlatformPrincipal(): JWTPrincipal =
    principal<JWTPrincipal>() ?: throw ApiException.Unauthorized()

fun ApplicationCall.requireActor(tokenService: AuthTokenService) =
    tokenService.userFrom(requirePlatformPrincipal().payload)

fun JWTPrincipal.subjectId(): String =
    payload.subject?.takeIf { it.isNotBlank() } ?: throw ApiException.Unauthorized()

fun JWTPrincipal.displayNameOr(default: String): String =
    payload.getClaim("display_name").asString()?.takeIf { it.isNotBlank() } ?: default

fun JWTPrincipal.platformRoles(): Set<UserRole> =
    payload.getClaim("roles")
        .asList(String::class.java)
        .orEmpty()
        .mapNotNull { encoded -> runCatching { UserRole.valueOf(encoded) }.getOrNull() }
        .toSet()

fun JWTPrincipal.requireRole(role: UserRole) {
    if (role !in platformRoles()) {
        throw ApiException.Forbidden("${role.name.lowercase()} role is required")
    }
}

fun JWTPrincipal.requireStaff() {
    if (platformRoles().none { it.isStaff }) {
        throw ApiException.Forbidden("Staff role is required")
    }
}

fun JWTPrincipal.requireAdministrative() {
    if (platformRoles().none { it.isAdministrative }) {
        throw ApiException.Forbidden("Administrative role is required")
    }
}

fun JWTPrincipal.hasAdministrativeScope(): Boolean =
    platformRoles().any { it.isAdministrative }
