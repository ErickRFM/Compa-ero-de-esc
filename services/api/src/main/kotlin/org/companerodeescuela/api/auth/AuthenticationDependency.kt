package org.companerodeescuela.api.auth

import kotlinx.coroutines.CancellationException
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.integrations.IntegrationException
import org.slf4j.LoggerFactory

private val dependencyLogger = LoggerFactory.getLogger("AuthenticationDependency")

/** Public auth failures carry no repository identifiers, credentials or upstream payloads. */
internal suspend fun <T> authenticationDependency(operation: suspend () -> T): T =
    try {
        operation()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: ApiException) {
        throw error
    } catch (error: IntegrationException) {
        if (error.category in setOf(IntegrationException.Category.UNAUTHORIZED, IntegrationException.Category.NOT_FOUND)) {
            throw ApiException.Unauthorized("Invalid username or password")
        }
        dependencyLogger.warn("Upstream integration unavailable category={}", error.category)
        throw ApiException.DependencyUnavailable("Authentication dependencies are temporarily unavailable")
    } catch (error: Exception) {
        dependencyLogger.warn("Authentication dependency failed cause={}", error::class.java.simpleName)
        throw ApiException.DependencyUnavailable("Authentication dependencies are temporarily unavailable")
    }
