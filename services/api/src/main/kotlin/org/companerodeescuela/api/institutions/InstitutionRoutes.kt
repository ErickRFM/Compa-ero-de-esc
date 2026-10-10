package org.companerodeescuela.api.institutions

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.companerodeescuela.api.auth.authenticationDependency
import org.companerodeescuela.api.plugins.requestId
import org.companerodeescuela.shared.contracts.ApiResponse

fun Route.institutionRoutes(institutions: InstitutionRepository) {
    get("/institutions") {
        call.respond(ApiResponse(authenticationDependency { institutions.listEnabled() }, call.requestId()))
    }
}
