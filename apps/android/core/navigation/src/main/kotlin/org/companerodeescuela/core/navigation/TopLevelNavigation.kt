package org.companerodeescuela.core.navigation

import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController

/**
 * Top-level tabs share one root. Returning to the root is a back-stack pop, not
 * a second navigation to an already existing Home destination.
 */
fun NavHostController.navigateToTopLevel(destination: Destination) {
    val start = graph.findStartDestination()
    if (destination.route == start.route) {
        if (currentDestination?.route == start.route) return
        if (popBackStack(start.id, inclusive = false)) return
    }

    navigate(destination.route) {
        popUpTo(start.id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Screen chrome must follow membership in the role-specific tab bar. */
internal fun isSecondaryRoute(
    currentRoute: String?,
    startRoute: String,
    topLevelRoutes: Collection<String>,
): Boolean =
    currentRoute != null &&
        currentRoute != startRoute &&
        currentRoute !in topLevelRoutes
