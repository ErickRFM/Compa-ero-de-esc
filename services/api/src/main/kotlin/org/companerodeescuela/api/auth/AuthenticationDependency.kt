package org.companerodeescuela.api.auth

import kotlinx.coroutines.CancellationException
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.integrations.IntegrationException

/** Public auth failures carry no repository identifiers, credentials or upstream payloads. */
internal suspend fun <T> authenticationDependency(operation: suspend () -> T): T = try {
    operation()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: ApiException) {
    throw error
} catch (error: IntegrationException) {
    if (error.category in setOf(IntegrationException.Category.UNAUTHORIZED, IntegrationException.Category.NOT_FOUND)) {
        throw ApiException.Unauthorized("Invalid username or password")
    }
    throw ApiException.DependencyUnavailable("Authentication dependencies are temporarily unavailable")
} catch (_: Exception) {
    throw ApiException.DependencyUnavailable("Authentication dependencies are temporarily unavailable")
}
