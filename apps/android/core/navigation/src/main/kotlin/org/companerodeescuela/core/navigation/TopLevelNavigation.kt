package org.companerodeescuela.core.navigation

import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController

/**
 * Top-level tabs share one root. Returning to the root is a back-stack pop, not
 * a second navigation to an already existing Home destination.
 */
fun NavHostController.navigateToTopLevel(destination: Destination) {
    if (currentDestination?.route == destination.route) return
    val start = graph.findStartDestination()
    if (destination.route == start.route) {
        if (currentDestination?.route == start.route) return
        if (popBackStack(start.id, inclusive = false, saveState = true)) return
    }

    // A contextual child may sit above its tab (Classes -> Channel). Return
    // to the tab itself rather than restoring that child's saved stack.
    if (popBackStack(destination.route, inclusive = false)) return

    navigate(destination.route) {
        popUpTo(start.id) { saveState = true }
        launchSingleTop = true
        restoreState = false
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
